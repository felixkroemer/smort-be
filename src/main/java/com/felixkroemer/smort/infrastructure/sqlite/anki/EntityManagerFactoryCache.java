package com.felixkroemer.smort.infrastructure.sqlite.anki;

import com.felixkroemer.smort.common.exception.SmortException;
import com.felixkroemer.smort.infrastructure.postgres.anki.AnalysisRepository;
import com.felixkroemer.smort.infrastructure.postgres.anki.AnalysisStatus;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.stereotype.Component;
import org.sqlite.Collation;
import org.sqlite.SQLiteDataSource;

@Component
@RequiredArgsConstructor
@Slf4j
public class EntityManagerFactoryCache {

  private final AnalysisRepository analysisRepository;

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

  public EntityManager getOrCreate(UUID analysisId) {

    var analysis =
        analysisRepository
            .findById(analysisId)
            .orElseThrow(
                () -> new SmortException("Could not find analysis by id. id={}", analysisId));

    if (analysis.getStatus() == AnalysisStatus.NEW) {
      throw new SmortException("Analysis is not ready. id={}", analysisId);
    }

    var dbPath = Path.of(analysis.getDbPath());

    return cache
        .get(
            analysisId,
            _ -> {
              var ds = new SQLiteDataSource();
              ds.setUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());

              var delegatingDS =
                  new DelegatingDataSource(ds) {
                    @Override
                    public @NonNull Connection getConnection() throws SQLException {
                      Connection conn = super.getConnection();
                      Collation.create(
                          conn,
                          "unicase",
                          new Collation() {
                            @Override
                            protected int xCompare(String s1, String s2) {
                              return s1.compareToIgnoreCase(s2);
                            }
                          });
                      return conn;
                    }
                  };

              var factory = getLocalContainerEMFBean(delegatingDS);
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
            })
        .createEntityManager();
  }

  private static @NonNull LocalContainerEntityManagerFactoryBean getLocalContainerEMFBean(
      DelegatingDataSource delegatingDS) {
    var factory = new LocalContainerEntityManagerFactoryBean();

    factory.setDataSource(delegatingDS);
    factory.setPackagesToScan("com.felixkroemer.smort.infrastructure.sqlite.anki");
    factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

    Properties props = new Properties();
    props.put("hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect");
    props.put("hibernate.hbm2ddl.auto", "validate");
    factory.setJpaProperties(props);
    return factory;
  }
}
