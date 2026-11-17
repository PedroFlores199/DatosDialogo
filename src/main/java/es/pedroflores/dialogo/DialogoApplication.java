package es.pedroflores.dialogo;

import org.apache.catalina.session.StandardManager;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@SpringBootApplication
public class DialogoApplication {
  public static void main(String[] args) {
    SpringApplication.run(DialogoApplication.class, args);
  }

  @Bean
  WebServerFactoryCustomizer<TomcatServletWebServerFactory> boundedSessions() {
    return factory ->
        factory.addContextCustomizers(
            context -> {
              var manager = new StandardManager();
              manager.setMaxActiveSessions(30);
              manager.setPathname(null);
              context.setManager(manager);
            });
  }

  @Bean
  PgVectorStore vectorStore(JdbcTemplate jdbc, EmbeddingModel embeddings) {
    return PgVectorStore.builder(jdbc, embeddings)
        .schemaName("rag")
        .vectorTableName("schema_embeddings")
        .dimensions(768)
        .initializeSchema(false)
        .build();
  }

  @Bean
  ReadonlyQueries readonlyQueries(Environment environment) {
    var dataSource = new DriverManagerDataSource();
    dataSource.setUrl(environment.getProperty("app.reader-url"));
    dataSource.setUsername(environment.getProperty("app.reader-user"));
    dataSource.setPassword(environment.getProperty("app.reader-password"));
    return new ReadonlyQueries(dataSource);
  }
}
