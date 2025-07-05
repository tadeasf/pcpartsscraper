package com.tadeasfort.pcpartsscraper.repository;

import com.tadeasfort.pcpartsscraper.model.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobStatusRepository extends JpaRepository<JobStatus, Long> {

    Optional<JobStatus> findByJobName(String jobName);

    @Query("SELECT js FROM JobStatus js WHERE js.completed = true")
    List<JobStatus> findCompletedJobs();

    @Query("SELECT js FROM JobStatus js WHERE js.completed = false")
    List<JobStatus> findIncompleteJobs();

    @Query("SELECT js FROM JobStatus js WHERE js.completed = true AND js.successful = true")
    List<JobStatus> findSuccessfulJobs();

    @Query("SELECT js FROM JobStatus js WHERE js.completed = true AND js.successful = false")
    List<JobStatus> findFailedJobs();

    @Query("SELECT COUNT(js) FROM JobStatus js WHERE js.jobName = :jobName AND js.completed = true AND js.successful = true")
    long countSuccessfulCompletions(@Param("jobName") String jobName);

    boolean existsByJobNameAndCompletedTrueAndSuccessfulTrue(String jobName);
}