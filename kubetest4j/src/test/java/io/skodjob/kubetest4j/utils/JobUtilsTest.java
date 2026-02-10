/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.utils;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodCondition;
import io.fabric8.kubernetes.api.model.PodConditionBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobCondition;
import io.fabric8.kubernetes.api.model.batch.v1.JobConditionBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.BatchAPIGroupDSL;
import io.fabric8.kubernetes.client.dsl.V1BatchAPIGroupDSL;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.ScalableResource;
import io.skodjob.kubetest4j.annotations.TestVisualSeparator;
import io.skodjob.kubetest4j.clients.KubeClient;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import io.skodjob.kubetest4j.wait.Wait;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.function.BooleanSupplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@TestVisualSeparator
@ExtendWith(MockitoExtension.class)
class JobUtilsTest {

    private static final String NAMESPACE = "test-namespace";
    private static final String JOB_NAME = "test-job";
    private static final String POD_NAME = JOB_NAME + "-abc";

    @Mock
    private KubeResourceManager mockKubeResourceManager;
    @Mock
    private KubeClient mockKubeClient;
    @Mock
    private KubernetesClient mockKubernetesClient;
    @Mock
    private BatchAPIGroupDSL mockBatchAPIGroupDSL;
    @Mock
    private V1BatchAPIGroupDSL mockV1BatchAPIGroupDSL;
    @Mock
    private MixedOperation<Job, JobList, ScalableResource<Job>> mockJobs;
    @Mock
    private NonNamespaceOperation<Job, JobList, ScalableResource<Job>> mockJobsInNamespace;
    @Mock
    private ScalableResource<Job> mockJobResource;

    private MockedStatic<KubeResourceManager> mockedStaticKubeResourceManager;
    private MockedStatic<Wait> mockedStaticWait;

    @BeforeEach
    void setUp() {
        mockedStaticKubeResourceManager = mockStatic(KubeResourceManager.class);
        lenient().when(KubeResourceManager.get()).thenReturn(mockKubeResourceManager);

        lenient().when(mockKubeResourceManager.kubeClient()).thenReturn(mockKubeClient);
        lenient().when(mockKubeClient.getClient()).thenReturn(mockKubernetesClient);
        lenient().when(mockKubernetesClient.batch()).thenReturn(mockBatchAPIGroupDSL);
        lenient().when(mockBatchAPIGroupDSL.v1()).thenReturn(mockV1BatchAPIGroupDSL);
        lenient().when(mockV1BatchAPIGroupDSL.jobs()).thenReturn(mockJobs);
        lenient().when(mockJobs.inNamespace(NAMESPACE)).thenReturn(mockJobsInNamespace);
        lenient().when(mockJobsInNamespace.withName(JOB_NAME)).thenReturn(mockJobResource);


        mockedStaticWait = mockStatic(Wait.class);
        mockedStaticWait.when(() -> Wait.until(anyString(), anyLong(), anyLong(), ArgumentMatchers.any()))
            .thenAnswer(invocation -> {
                BooleanSupplier check = invocation.getArgument(3);
                return check.getAsBoolean();
            });

        lenient().when(mockKubeClient.listPodsByPrefixInName(NAMESPACE, JOB_NAME))
            .thenReturn(Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        mockedStaticKubeResourceManager.close();
        mockedStaticWait.close();
    }

    @Test
    void testWaitForJobContainingLogMessage() {
        Pod mockPod = new PodBuilder().withNewMetadata().withName(POD_NAME).endMetadata().build();
        when(mockKubeClient.listPodsByPrefixInName(NAMESPACE, JOB_NAME))
            .thenReturn(Collections.singletonList(mockPod));
        when(mockKubeClient.getLogsFromPod(NAMESPACE, POD_NAME))
            .thenReturn("This is a desired log message");

        JobUtils.waitForJobContainingLogMessage(NAMESPACE, JOB_NAME, "This is a desired log message");

        verify(mockKubeClient, times(1)).listPodsByPrefixInName(NAMESPACE, JOB_NAME);
        verify(mockKubeClient, times(1)).getLogsFromPod(NAMESPACE, POD_NAME);
        mockedStaticWait.verify(() -> Wait.until(
            anyString(),
            anyLong(),
            anyLong(),
            any(BooleanSupplier.class)
        ));
    }

    @Test
    void testRemoveAllJobs() {
        Job mockJob = new JobBuilder().withNewMetadata().withName(JOB_NAME).endMetadata().build();
        JobList mockJobList = mock(JobList.class);
        when(mockJobList.getItems()).thenReturn(Collections.singletonList(mockJob));
        when(mockJobsInNamespace.list()).thenReturn(mockJobList);

        doAnswer(invocation -> {
            lenient().when(mockKubeClient.listPodsByPrefixInName(NAMESPACE, JOB_NAME))
                .thenReturn(Collections.emptyList());
            return null;
        }).when(mockJobResource).delete();

        JobUtils.removeAllJobs(NAMESPACE);

        verify(mockJobsInNamespace, times(1)).list();
        verify(mockJobList, times(1)).getItems();
        verify(mockJobResource, times(1)).delete();
        verify(mockKubeClient, times(1)).listPodsByPrefixInName(NAMESPACE, JOB_NAME);
    }

    @Test
    void testWaitForJobDeletion() {
        when(mockKubeClient.listPodsByPrefixInName(NAMESPACE, JOB_NAME))
            .thenReturn(Collections.singletonList(new Pod()))
            .thenReturn(Collections.emptyList());

        JobUtils.waitForJobDeletion(NAMESPACE, JOB_NAME);

        verify(mockKubeClient, times(1)).listPodsByPrefixInName(NAMESPACE, JOB_NAME);
        mockedStaticWait.verify(() -> Wait.until(
            anyString(),
            anyLong(),
            anyLong(),
            any(BooleanSupplier.class)
        ));
    }

    @Test
    void testDeleteJobWithWait() {
        doAnswer(invocation -> {
            lenient().when(mockKubeClient.listPodsByPrefixInName(NAMESPACE, JOB_NAME))
                .thenReturn(Collections.emptyList());
            return null;
        }).when(mockJobResource).delete();

        when(mockKubeClient.listPodsByPrefixInName(NAMESPACE, JOB_NAME))
            .thenReturn(Collections.singletonList(new Pod()));

        JobUtils.deleteJobWithWait(NAMESPACE, JOB_NAME);

        verify(mockJobResource, times(1)).delete();
        verify(mockKubeClient, times(1)).listPodsByPrefixInName(NAMESPACE, JOB_NAME);
    }

    @Test
    void testWaitForJobSuccess() {
        Job successfulJob = new JobBuilder().withNewStatus().withSucceeded(1).endStatus().build();
        when(mockJobResource.get()).thenReturn(successfulJob);

        JobUtils.waitForJobSuccess(NAMESPACE, JOB_NAME, 100L);

        verify(mockJobResource, times(1)).get();
        mockedStaticWait.verify(() -> Wait.until(
            anyString(),
            anyLong(),
            anyLong(),
            any(BooleanSupplier.class)
        ));
    }

    @Test
    void testWaitForJobFailure() {
        Job failedJob = new JobBuilder().withNewStatus().withFailed(1).endStatus().build();
        when(mockJobResource.get()).thenReturn(failedJob);

        JobUtils.waitForJobFailure(NAMESPACE, JOB_NAME, 100L);

        verify(mockJobResource, times(1)).get();
        mockedStaticWait.verify(() -> Wait.until(
            anyString(),
            anyLong(),
            anyLong(),
            any(BooleanSupplier.class)
        ));
    }

    @Test
    void testLogCurrentJobStatusWithConditionsAndPods() {
        JobCondition jobConditionWithMessage = new JobConditionBuilder()
            .withType("Complete")
            .withMessage("Job completed successfully")
            .build();
        JobCondition jobConditionWithoutMessage = new JobConditionBuilder()
            .withType("Progressing")
            .build();

        Job mockJob = new JobBuilder()
            .withNewMetadata().withName(JOB_NAME).endMetadata()
            .withNewStatus()
            .withActive(0)
            .withFailed(0)
            .withReady(0)
            .withSucceeded(1)
            .withConditions(jobConditionWithMessage, jobConditionWithoutMessage)
            .endStatus()
            .build();

        PodCondition podConditionWithMessage = new PodConditionBuilder()
            .withType("Ready")
            .withMessage("Pod is ready")
            .build();
        PodCondition podConditionWithoutMessage = new PodConditionBuilder()
            .withType("Initialized")
            .build();

        Pod mockPod = new PodBuilder()
            .withNewMetadata().withName(POD_NAME).endMetadata()
            .withNewStatus().withConditions(podConditionWithMessage, podConditionWithoutMessage).endStatus()
            .build();

        when(mockJobResource.get()).thenReturn(mockJob);
        when(mockKubeClient.listPodsByPrefixInName(NAMESPACE, JOB_NAME))
            .thenReturn(Collections.singletonList(mockPod));

        JobUtils.logCurrentJobStatus(NAMESPACE, JOB_NAME);

        verify(mockJobResource, times(1)).get();
        verify(mockKubeClient, times(1)).listPodsByPrefixInName(NAMESPACE, JOB_NAME);
    }

    @Test
    void testLogCurrentJobStatusNoConditionsNoPods() {
        Job mockJob = new JobBuilder()
            .withNewMetadata().withName(JOB_NAME).endMetadata()
            .withNewStatus()
            .withActive(0)
            .withFailed(0)
            .withReady(0)
            .withSucceeded(1)
            .endStatus()
            .build();

        when(mockJobResource.get()).thenReturn(mockJob);
        when(mockKubeClient.listPodsByPrefixInName(NAMESPACE, JOB_NAME))
            .thenReturn(Collections.emptyList());

        JobUtils.logCurrentJobStatus(NAMESPACE, JOB_NAME);

        verify(mockJobResource, times(1)).get();
        verify(mockKubeClient, times(1)).listPodsByPrefixInName(NAMESPACE, JOB_NAME);
    }

    @Test
    void testLogCurrentJobStatusNullJob() {
        when(mockJobResource.get()).thenReturn(null);

        JobUtils.logCurrentJobStatus(NAMESPACE, JOB_NAME);

        verify(mockJobResource, times(1)).get();
        verify(mockKubeClient, never()).listPodsByPrefixInName(anyString(), anyString());
    }

    @Test
    void testLogCurrentJobStatusNullJobStatus() {
        Job mockJob = new JobBuilder()
            .withNewMetadata().withName(JOB_NAME).endMetadata()
            .build();

        when(mockJobResource.get()).thenReturn(mockJob);

        JobUtils.logCurrentJobStatus(NAMESPACE, JOB_NAME);

        verify(mockJobResource, times(1)).get();
        verify(mockKubeClient, never()).listPodsByPrefixInName(anyString(), anyString());
    }

    @Test
    void testLogCurrentJobStatusPodWithEmptyConditions() {
        Job mockJob = new JobBuilder()
            .withNewMetadata().withName(JOB_NAME).endMetadata()
            .withNewStatus().withSucceeded(1).endStatus()
            .build();
        Pod mockPod = new PodBuilder()
            .withNewMetadata().withName(POD_NAME).endMetadata()
            .withNewStatus().withConditions(Collections.emptyList()).endStatus()
            .build();

        when(mockJobResource.get()).thenReturn(mockJob);
        when(mockKubeClient.listPodsByPrefixInName(NAMESPACE, JOB_NAME))
            .thenReturn(Collections.singletonList(mockPod));

        JobUtils.logCurrentJobStatus(NAMESPACE, JOB_NAME);

        verify(mockJobResource, times(1)).get();
        verify(mockKubeClient, times(1)).listPodsByPrefixInName(NAMESPACE, JOB_NAME);
    }
}
