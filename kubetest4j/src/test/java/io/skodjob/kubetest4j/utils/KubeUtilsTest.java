/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.utils;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeBuilder;
import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.api.model.NodeListBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.InstallPlan;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.InstallPlanBuilder;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.InstallPlanList;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.dsl.OpenShiftOperatorHubAPIGroupDSL;
import io.skodjob.kubetest4j.annotations.TestVisualSeparator;
import io.skodjob.kubetest4j.clients.KubeClient;
import io.skodjob.kubetest4j.clients.cmdClient.BaseCmdKubeClient;
import io.skodjob.kubetest4j.executor.ExecResult;
import io.skodjob.kubetest4j.resources.KubeResourceManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@TestVisualSeparator
class KubeUtilsTest {
    static KubeResourceManager kubeResourceManager = mock(KubeResourceManager.class);
    static KubernetesClient kubernetesClient = mock(KubernetesClient.class);
    static KubeClient kubeClient = mock(KubeClient.class);
    static BaseCmdKubeClient cmdClient = mock(BaseCmdKubeClient.class);
    static OpenShiftClient openShiftClient = mock(OpenShiftClient.class);
    static OpenShiftOperatorHubAPIGroupDSL operatorHubDSL = mock(OpenShiftOperatorHubAPIGroupDSL.class);

    @BeforeAll
    static void setup() {
        when(kubeResourceManager.kubeClient()).thenReturn(kubeClient);
        when(kubeResourceManager.kubeCmdClient()).thenReturn(cmdClient);
        when(kubeClient.getClient()).thenReturn(kubernetesClient);
        when(kubeClient.getOpenShiftClient()).thenReturn(openShiftClient);
        when(openShiftClient.operatorHub()).thenReturn(operatorHubDSL);
    }

    @Test
    void testLabelNamespaceNotExists() {
        try (MockedStatic<KubeResourceManager> mockedStatic = mockStatic(KubeResourceManager.class)) {
            when(KubeResourceManager.get()).thenReturn(kubeResourceManager);

            when(kubeClient.namespaceExists(anyString())).thenReturn(false);

            KubeUtils.labelNamespace("non-existent-namespace", "test-key", "test-value");
        }
    }

    @Test
    void testIsOcpTrue() {
        try (MockedStatic<KubeResourceManager> mockedStatic = mockStatic(KubeResourceManager.class)) {
            when(KubeResourceManager.get()).thenReturn(kubeResourceManager);

            ExecResult execResult = mock(ExecResult.class);
            when(cmdClient.exec(anyBoolean(), anyBoolean(), anyString())).thenReturn(execResult);
            when(execResult.out()).thenReturn("apps.openshift.io/v1\nconfig.openshift.io/v1\n");

            boolean result = KubeUtils.isOcp();

            assertTrue(result);
        }
    }

    @Test
    void testIsOcpFalse() {
        try (MockedStatic<KubeResourceManager> mockedStatic = mockStatic(KubeResourceManager.class)) {
            when(KubeResourceManager.get()).thenReturn(kubeResourceManager);

            ExecResult execResult = mock(ExecResult.class);
            when(cmdClient.exec(anyBoolean(), anyBoolean(), anyString())).thenReturn(execResult);
            when(execResult.out()).thenReturn("apps/v1\nextensions/v1beta1\n");

            boolean result = KubeUtils.isOcp();

            assertFalse(result);
        }
    }

    @Test
    void testIsMultinodeTrue() {
        try (MockedStatic<KubeResourceManager> mockedStatic = mockStatic(KubeResourceManager.class)) {
            when(KubeResourceManager.get()).thenReturn(kubeResourceManager);

            @SuppressWarnings("unchecked")
            NonNamespaceOperation<Node, NodeList, Resource<Node>> nodesOp = mock(NonNamespaceOperation.class);

            Node node1 = new NodeBuilder()
                .withNewMetadata()
                .withName("node1")
                .endMetadata()
                .build();

            Node node2 = new NodeBuilder()
                .withNewMetadata()
                .withName("node2")
                .endMetadata()
                .build();

            NodeList nodeList = new NodeListBuilder()
                .withItems(Arrays.asList(node1, node2))
                .build();

            when(kubernetesClient.nodes()).thenReturn(nodesOp);
            when(nodesOp.list()).thenReturn(nodeList);

            boolean result = KubeUtils.isMultinode();

            assertTrue(result);
        }
    }

    @Test
    void testIsMultinodeFalse() {
        try (MockedStatic<KubeResourceManager> mockedStatic = mockStatic(KubeResourceManager.class)) {
            when(KubeResourceManager.get()).thenReturn(kubeResourceManager);

            @SuppressWarnings("unchecked")
            NonNamespaceOperation<Node, NodeList, Resource<Node>> nodesOp = mock(NonNamespaceOperation.class);

            Node node1 = new NodeBuilder()
                .withNewMetadata()
                .withName("node1")
                .endMetadata()
                .build();

            NodeList nodeList = new NodeListBuilder()
                .withItems(Collections.singletonList(node1))
                .build();

            when(kubernetesClient.nodes()).thenReturn(nodesOp);
            when(nodesOp.list()).thenReturn(nodeList);

            boolean result = KubeUtils.isMultinode();

            assertFalse(result);
        }
    }

    @Test
    void testLabelNamespaceSuccess() {
        try (MockedStatic<KubeResourceManager> mockedStatic = mockStatic(KubeResourceManager.class)) {
            when(KubeResourceManager.get()).thenReturn(kubeResourceManager);

            @SuppressWarnings("unchecked")
            NonNamespaceOperation<Namespace, io.fabric8.kubernetes.api.model.NamespaceList,
                Resource<Namespace>> namespacesOp = mock(NonNamespaceOperation.class);
            @SuppressWarnings("unchecked")
            Resource<Namespace> namespaceResource = mock(Resource.class);

            Namespace labeledNamespace = new NamespaceBuilder()
                .withNewMetadata()
                .withName("test-namespace")
                .addToLabels("test-key", "test-value")
                .endMetadata()
                .build();

            when(kubeClient.namespaceExists(anyString())).thenReturn(true);
            when(kubernetesClient.namespaces()).thenReturn(namespacesOp);
            when(namespacesOp.withName(anyString())).thenReturn(namespaceResource);
            when(namespaceResource.edit((UnaryOperator<Namespace>) any()))
                .thenReturn(labeledNamespace);
            when(namespaceResource.get()).thenReturn(labeledNamespace);

            KubeUtils.labelNamespace("test-namespace", "test-key", "test-value");
        }
    }

    @Test
    void testLabelNamespaceFailedEditing() {
        try (MockedStatic<KubeResourceManager> mockedStatic = mockStatic(KubeResourceManager.class)) {
            when(KubeResourceManager.get()).thenReturn(kubeResourceManager);

            @SuppressWarnings("unchecked")
            NonNamespaceOperation<Namespace, io.fabric8.kubernetes.api.model.NamespaceList,
                Resource<Namespace>> namespacesOp = mock(NonNamespaceOperation.class);
            @SuppressWarnings("unchecked")
            Resource<Namespace> namespaceResource = mock(Resource.class);

            Namespace labeledNamespace = new NamespaceBuilder()
                .withNewMetadata()
                .withName("test-namespace")
                .addToLabels("test-key", "test-value")
                .endMetadata()
                .build();

            when(kubeClient.namespaceExists(anyString())).thenReturn(true);
            when(kubernetesClient.namespaces()).thenReturn(namespacesOp);
            when(namespacesOp.withName(anyString())).thenReturn(namespaceResource);
            // First call fails, second call succeeds
            when(namespaceResource.edit((UnaryOperator<Namespace>) any()))
                .thenThrow(new RuntimeException("Failed to edit namespace"))
                .thenReturn(labeledNamespace);
            when(namespaceResource.get()).thenReturn(labeledNamespace);

            KubeUtils.labelNamespace("test-namespace", "test-key", "test-value");
        }
    }

    @Test
    void testApproveInstallPlan() {
        try (MockedStatic<KubeResourceManager> mockedStatic = mockStatic(KubeResourceManager.class)) {
            when(KubeResourceManager.get()).thenReturn(kubeResourceManager);

            @SuppressWarnings("unchecked")
            MixedOperation<InstallPlan, InstallPlanList, Resource<InstallPlan>> installPlansOp =
                mock(MixedOperation.class);
            @SuppressWarnings("unchecked")
            Resource<InstallPlan> installPlanResource = mock(Resource.class);

            InstallPlan existingInstallPlan = new InstallPlanBuilder()
                .withNewMetadata()
                .withName("test-install-plan")
                .withNamespace("test-namespace")
                .endMetadata()
                .withNewSpec()
                .withApproved(false)
                .endSpec()
                .build();

            InstallPlan approvedInstallPlan = new InstallPlanBuilder()
                .withNewMetadata()
                .withName("test-install-plan")
                .withNamespace("test-namespace")
                .endMetadata()
                .withNewSpec()
                .withApproved(true)
                .endSpec()
                .build();

            when(operatorHubDSL.installPlans()).thenReturn(installPlansOp);
            when(installPlansOp.inNamespace("test-namespace")).thenReturn(installPlansOp);
            when(installPlansOp.withName("test-install-plan")).thenReturn(installPlanResource);
            when(installPlanResource.get()).thenReturn(existingInstallPlan);
            when(installPlanResource.patch(any(InstallPlan.class))).thenReturn(approvedInstallPlan);

            KubeUtils.approveInstallPlan("test-namespace", "test-install-plan");
        }
    }

    @Test
    void testGetNonApprovedInstallPlanFound() {
        try (MockedStatic<KubeResourceManager> mockedStatic = mockStatic(KubeResourceManager.class)) {
            when(KubeResourceManager.get()).thenReturn(kubeResourceManager);

            @SuppressWarnings("unchecked")
            MixedOperation<InstallPlan, InstallPlanList, Resource<InstallPlan>> installPlansOp =
                mock(MixedOperation.class);

            InstallPlan nonApprovedPlan = new InstallPlanBuilder()
                .withNewMetadata()
                .withName("non-approved-plan")
                .withNamespace("test-namespace")
                .endMetadata()
                .withNewSpec()
                .withApproved(false)
                .withClusterServiceVersionNames(List.of("my-operator.v1.0.0"))
                .endSpec()
                .build();

            InstallPlan approvedPlan = new InstallPlanBuilder()
                .withNewMetadata()
                .withName("approved-plan")
                .withNamespace("test-namespace")
                .endMetadata()
                .withNewSpec()
                .withApproved(true)
                .withClusterServiceVersionNames(List.of("my-operator.v1.1.0"))
                .endSpec()
                .build();

            InstallPlanList installPlanList = new InstallPlanList();
            installPlanList.setItems(Arrays.asList(nonApprovedPlan, approvedPlan));

            when(operatorHubDSL.installPlans()).thenReturn(installPlansOp);
            when(installPlansOp.inNamespace("test-namespace")).thenReturn(installPlansOp);
            when(installPlansOp.list()).thenReturn(installPlanList);

            InstallPlan result = KubeUtils.getNonApprovedInstallPlan("test-namespace", "my-operator");

            assertNotNull(result);
        }
    }

    @Test
    void testGetNonApprovedInstallPlanNotFound() {
        try (MockedStatic<KubeResourceManager> mockedStatic = mockStatic(KubeResourceManager.class)) {
            when(KubeResourceManager.get()).thenReturn(kubeResourceManager);

            @SuppressWarnings("unchecked")
            MixedOperation<InstallPlan, InstallPlanList, Resource<InstallPlan>> installPlansOp =
                mock(MixedOperation.class);

            InstallPlan approvedPlan = new InstallPlanBuilder()
                .withNewMetadata()
                .withName("approved-plan")
                .withNamespace("test-namespace")
                .endMetadata()
                .withNewSpec()
                .withApproved(true)
                .withClusterServiceVersionNames(List.of("other-operator.v1.0.0"))
                .endSpec()
                .build();

            InstallPlanList installPlanList = new InstallPlanList();
            installPlanList.setItems(Collections.singletonList(approvedPlan));

            when(operatorHubDSL.installPlans()).thenReturn(installPlansOp);
            when(installPlansOp.inNamespace("test-namespace")).thenReturn(installPlansOp);
            when(installPlansOp.list()).thenReturn(installPlanList);

            InstallPlan result = KubeUtils.getNonApprovedInstallPlan("test-namespace", "my-operator");

            assertNull(result);
        }
    }
}