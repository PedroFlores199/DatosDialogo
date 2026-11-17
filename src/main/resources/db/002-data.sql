\copy analytics.dim_province FROM '/seed/dim_province.csv' WITH (FORMAT csv, HEADER true, NULL '');
\copy analytics.dim_date FROM '/seed/dim_date.csv' WITH (FORMAT csv, HEADER true, NULL '');
\copy analytics.fact_hotel FROM '/seed/fact_hotel.csv' WITH (FORMAT csv, HEADER true, NULL '');
\copy analytics.fact_markets_annual FROM '/seed/fact_markets_annual.csv' WITH (FORMAT csv, HEADER true, NULL '');
\copy analytics.fact_region_monthly FROM '/seed/fact_region_monthly.csv' WITH (FORMAT csv, HEADER true, NULL '');
\copy analytics.fact_housing FROM '/seed/fact_housing.csv' WITH (FORMAT csv, HEADER true, NULL '');
\copy analytics.fact_forecast FROM '/seed/fact_forecast.csv' WITH (FORMAT csv, HEADER true, NULL '');
ANALYZE;
