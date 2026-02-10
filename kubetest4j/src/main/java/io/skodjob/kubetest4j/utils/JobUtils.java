/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.utils;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodCondition;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobCondition;
import io.skodjob.kubetest4j.KubeTestConstants;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import io.skodjob.kubetest4j.wait.Wait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;

/**
 * Class containing job utils
 */
public final class JobUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobUtils.class);

    private JobUtils() {
        // Private constructor to prevent instantiation
    }

    /**
     * Wait until the Pod of Job with {@param jobName} contains specified {@param logMessage}
     *
     * @param namespace  name of Namespace where the Pod is running
     * @param jobName    name of Job with which the Pod name obtained
     * @param logMessage desired log message
     */
    public static void waitForJobContainingLogMessage(String namespace, String jobName, String logMessage) {
        String jobPodName = KubeResourceManager.get().kubeClient()
            .listPodsByPrefixInName(namespace, jobName).get(0).getMetadata().getName();

        Wait.until("Job contains log message: " + logMessage,
            KubeTestConstants.GLOBAL_POLL_INTERVAL_LONG, KubeTestConstants.GLOBAL_TIMEOUT,
            () -> KubeResourceManager.get().kubeClient().getLogsFromPod(namespace, jobPodName).contains(logMessage));
    }

    /**
     * Wait until all Jobs are deleted in given namespace.
     *
     * @param namespace Delete all jobs in this namespace
     */
    public static void removeAllJobs(String namespace) {
        KubeResourceManager.get().kubeClient().getClient()
            .batch().v1().jobs().inNamespace(namespace).list().getItems().forEach(
                job -> JobUtils.deleteJobWithWait(namespace, job.getMetadata().getName()));
    }

    /**
     * Wait until the given Job has been deleted.
     *
     * @param namespace name of the Namespace
     * @param jobName   name of the job
     */
    public static void waitForJobDeletion(final String namespace, String jobName) {
        LOGGER.debug("Waiting for Job: {}/{} deletion", namespace, jobName);
        Wait.until("deletion of Job: " + namespace + "/" + jobName,
            KubeTestConstants.GLOBAL_POLL_INTERVAL_1_SEC, KubeTestConstants.GLOBAL_TIMEOUT_MEDIUM,
            () -> KubeResourceManager.get().kubeClient().listPodsByPrefixInName(namespace, jobName).isEmpty());
        LOGGER.debug("Job: {}/{} was deleted", namespace, jobName);
    }

    /**
     * Delete Job and wait for it's deletion
     *
     * @param namespace name of the Namespace
     * @param jobName   name of the job
     */
    public static void deleteJobWithWait(String namespace, String jobName) {
        KubeResourceManager.get().kubeClient().getClient()
            .batch().v1().jobs().inNamespace(namespace).withName(jobName).delete();
        waitForJobDeletion(namespace, jobName);
    }

    /**
     * Wait for specific Job success
     *
     * @param namespace name of the Namespace
     * @param jobName   name of the job
     * @param timeout   timeout in ms after which we assume that job failed
     */
    public static void waitForJobSuccess(String namespace, String jobName, long timeout) {
        LOGGER.info("Waiting for Job: {}/{} to success", namespace, jobName);
        Wait.until("success of Job: " + namespace + "/" + jobName,
            KubeTestConstants.GLOBAL_POLL_INTERVAL_1_SEC, timeout,
            () -> KubeResourceManager.get().kubeClient().getClient().batch().v1().jobs()
                .inNamespace(namespace).withName(jobName).get().getStatus().getSucceeded() != null);
    }

    /**
     * Wait for specific Job failure
     *
     * @param namespace name of the Namespace
     * @param jobName   name of the job
     * @param timeout   timeout in ms after which we assume that job failed
     */
    public static void waitForJobFailure(String namespace, String jobName, long timeout) {
        LOGGER.info("Waiting for Job: {}/{} to fail", namespace, jobName);
        Wait.until("failure of Job: " + namespace + "/" + jobName,
            KubeTestConstants.GLOBAL_POLL_INTERVAL_1_SEC, timeout,
            () -> KubeResourceManager.get().kubeClient().getClient().batch().v1().jobs()
                .inNamespace(namespace).withName(jobName).get().getStatus().getFailed() != null);
    }

    /**
     * Log actual status of Job with pods.
     *
     * @param namespace namespace/project where is job running
     * @param jobName   name of the job, for which we should scrape status
     */
    public static void logCurrentJobStatus(String namespace, String jobName) {
        Job currentJob = KubeResourceManager.get().kubeClient().getClient().batch().v1().jobs()
            .inNamespace(namespace).withName(jobName).get();

        if (currentJob != null && currentJob.getStatus() != null) {
            List<String> log = new ArrayList<>(asList("job", " status:\n"));

            List<JobCondition> conditions = currentJob.getStatus().getConditions();

            log.add("\tActive: " + currentJob.getStatus().getActive());
            log.add("\n\tFailed: " + currentJob.getStatus().getFailed());
            log.add("\n\tReady: " + currentJob.getStatus().getReady());
            log.add("\n\tSucceeded: " + currentJob.getStatus().getSucceeded());

            if (conditions != null) {
                List<String> conditionList = new ArrayList<>();

                for (JobCondition condition : conditions) {
                    if (condition.getMessage() != null) {
                        conditionList.add("\t\tType: " + condition.getType() + "\n");
                        conditionList.add("\t\tMessage: " + condition.getMessage() + "\n");
                    }
                }

                if (!conditionList.isEmpty()) {
                    log.add("\n\tConditions:\n");
                    log.addAll(conditionList);
                }
            }

            log.add("\n\nPods with conditions and messages:\n\n");

            for (Pod pod : KubeResourceManager.get().kubeClient().listPodsByPrefixInName(namespace, jobName)) {
                log.add(pod.getMetadata().getName() + ":");
                List<String> podConditions = new ArrayList<>();

                for (PodCondition podCondition : pod.getStatus().getConditions()) {
                    if (podCondition.getMessage() != null) {
                        podConditions.add("\n\tType: " + podCondition.getType() + "\n");
                        podConditions.add("\tMessage: " + podCondition.getMessage() + "\n");
                    }
                }

                if (podConditions.isEmpty()) {
                    log.add("\n\t<EMPTY>");
                } else {
                    log.addAll(podConditions);
                }
                log.add("\n\n");
            }
            LOGGER.info("{}", String.join("", log).strip());
        }
    }
}
