package com.felixkroemer.smort.infrastructure.sqlite.anki;

import com.felixkroemer.smort.common.exception.SmortException;
import com.felixkroemer.smort.infrastructure.postgres.anki.AnkiAnalysisRepository;
import com.felixkroemer.smort.infrastructure.postgres.anki.AnkiAnalysisStatus;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.persistence.EntityManagerFactory;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.stereotype.Component;
import org.sqlite.SQLiteDataSource;

@Component
@RequiredArgsConstructor
@Slf4j
public class EntityManagerFactoryCache {

  private final AnkiAnalysisRepository ankiAnalysisRepository;

  private final Cache<UUID, EntityManagerFactory> cache =
      Caffeine.newBuilder()
          .expireAfterAccess(30, TimeUnit.MINUTES)
          .<UUID, EntityManagerFactory>removalListener(
              (_, emf, _) -> {
                if (emf != null) {
                  emf.close();
                }
              })
          .build();

  public EntityManagerFactory getOrCreate(UUID analysisId) {

    var ankiAnalysis =
        ankiAnalysisRepository
            .findById(analysisId)
            .orElseThrow(
                () -> new SmortException("Could not find analysis by id. id={}", analysisId));

    if (ankiAnalysis.getStatus() != AnkiAnalysisStatus.READY) {
      throw new SmortException("Analysis is not ready. id={}", analysisId);
    }

    var dbPath = Path.of(ankiAnalysis.getDbPath());

    return cache.get(
        analysisId,
        _ -> {
          SQLiteDataSource ds = new SQLiteDataSource();
          ds.setUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());

          LocalContainerEntityManagerFactoryBean factory =
              new LocalContainerEntityManagerFactoryBean();

          factory.setDataSource(ds);
          factory.setPackagesToScan("com.felixkroemer.smort.infrastructure.sqlite.anki");
          factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

          Properties props = new Properties();
          props.put("hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect");
          props.put("hibernate.hbm2ddl.auto", "validate");
          factory.setJpaProperties(props);
          try {
            factory.afterPropertiesSet();
            return factory.getObject();
          } catch (Exception e) {
            throw new SmortException(
                "Failed to initialize EntityManagerFactory. id={}, dbPath={}",
                analysisId,
                dbPath,
                e);
          }
        });
  }
}
