import argparse
from datetime import date, datetime, timezone
from decimal import Decimal
import json
import math
import os
from pathlib import Path
import re
import statistics
import time

import httpx
import psycopg


def normalize(value):
    if isinstance(value, (date, datetime)):
        return value.isoformat()[:10]
    if isinstance(value, Decimal):
        return float(value)
    if isinstance(value, str):
        if re.match(r'^\d{4}-\d{2}-\d{2}',value):
            return value[:10]
        if re.fullmatch(r'\d{1,4}',value):
            return int(value)
    return value


def equivalent(actual, expected):
    if len(actual)!=len(expected):
        return False
    for left,right in zip(actual,expected):
        if len(left)!=len(right):
            return False
        for a,b in zip(left,right):
            a,b=normalize(a),normalize(b)
            if isinstance(a,(int,float)) and isinstance(b,(int,float)):
                if not math.isclose(a,b,rel_tol=1e-9,abs_tol=.011):
                    return False
            elif a!=b:
                return False
    return True


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--url',default='http://localhost:5104')
    parser.add_argument('--output',type=Path,default=Path(__file__).parent/'results.json')
    parser.add_argument('--questions',type=Path,default=Path(__file__).parent/'questions.json')
    parser.add_argument('--model',default=os.getenv('CHAT_MODEL','qwen3:4b-instruct-2507-q4_K_M'))
    args=parser.parse_args()
    with httpx.Client(timeout=5, trust_env=False) as probe:
        deadline=time.monotonic()+180
        while True:
            try:
                health=probe.get(args.url+'/actuator/health/readiness')
                if health.status_code==200 and health.json().get('status')=='UP':
                    break
            except (httpx.HTTPError, ValueError):
                pass
            if time.monotonic()>=deadline:
                raise RuntimeError('The application did not become ready within 180 seconds')
            time.sleep(2)
    cases=json.loads(args.questions.read_text())
    clients={}
    results=[]
    with psycopg.connect(os.getenv('EVAL_DATABASE_URL','postgresql://analytics_reader:local-reader-demo@localhost:55434/dialogo'),autocommit=True) as database:
        for item in cases:
            group=item.get('conversation',item['id'])
            if group not in clients:
                clients[group]=httpx.Client(timeout=120,trust_env=False)
            client=clients[group]
            expected=[]
            if item.get('reference_sql'):
                with database.cursor() as cursor:
                    cursor.execute(item['reference_sql'])
                    expected=[list(row) for row in cursor.fetchall()]
            started=time.monotonic()
            try:
                response=client.post(args.url+'/api/chat',json={'question':item['question']})
                payload=response.json()
                actual_result=payload.get('result')
                actual=[[row.get(column) for column in actual_result['columns']] for row in actual_result['rows']] if actual_result else []
                passed=(response.status_code==200 and payload.get('status')=='unsupported' and actual_result is None) if item.get('expected_status')=='refused' else response.status_code==200 and payload.get('status')=='ok' and equivalent(actual,expected)
                result={**item,'passed':passed,'http_status':response.status_code,'actual_status':payload.get('status'),'actual_sql':actual_result['sql'] if actual_result else None,'expected_rows':expected,'actual_rows':actual,'attempts':payload.get('attempts'),'steps':payload.get('steps'),'seconds':round(time.monotonic()-started,3)}
            except (httpx.HTTPError,ValueError) as error:
                result={**item,'passed':False,'error':str(error),'seconds':round(time.monotonic()-started,3)}
            results.append(result)
            print(json.dumps({'id':item['id'],'passed':result['passed'],'seconds':result['seconds'],'sql':result.get('actual_sql')},ensure_ascii=False),flush=True)
            args.output.parent.mkdir(parents=True,exist_ok=True)
            args.output.write_text(json.dumps({'complete':False,'results':results},ensure_ascii=False,indent=2,default=str))
            if 'conversation' not in item or item.get('end_conversation'):
                try:
                    client.delete(args.url+'/api/session')
                except httpx.HTTPError:
                    pass
                finally:
                    client.close()
                    clients.pop(group,None)
    for client in clients.values():
        client.close()
    supported=[r for r in results if r.get('reference_sql')]
    refused=[r for r in results if r.get('expected_status')=='refused']
    report={'complete':True,'at':datetime.now(timezone.utc).isoformat(),'model':args.model,'environment':'Local Ollama / Metal / Mac M2 Max; Java 21 / Spring AI 1.1.8 / PostgreSQL 17','total':len(results),'passed':sum(r['passed'] for r in results),'sql_cases':len(supported),'sql_execution_accuracy':sum(r['passed'] for r in supported)/len(supported),'refusal_cases':len(refused),'correct_refusals':sum(r['passed'] for r in refused),'median_seconds':statistics.median(r['seconds'] for r in results),'comparator':'Ordered result rows; aliases ignored; dates normalized to ISO date; numerical absolute tolerance 0.011 and relative tolerance 1e-9. Refusals scored separately and require unsupported status. No judge model.','results':results}
    args.output.write_text(json.dumps(report,ensure_ascii=False,indent=2,default=str))
    print(json.dumps({k:v for k,v in report.items() if k!='results'},ensure_ascii=False),flush=True)


if __name__=='__main__':
    main()
