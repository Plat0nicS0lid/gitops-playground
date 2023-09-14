package com.cloudogu.gitops

import com.cloudogu.gitops.config.ApplicationConfigurator
import com.cloudogu.gitops.config.schema.JsonSchemaGenerator
import com.cloudogu.gitops.config.schema.SchemaValidator
import com.cloudogu.gitops.utils.FileSystemUtils
import com.cloudogu.gitops.utils.NetworkingUtils
import com.cloudogu.gitops.utils.TestLogger
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static com.github.stefanbirkner.systemlambda.SystemLambda.withEnvironmentVariable
import static groovy.test.GroovyAssert.shouldFail
import static org.assertj.core.api.Assertions.assertThat
import static org.mockito.ArgumentMatchers.anyString
import static org.mockito.Mockito.mock
import static org.mockito.Mockito.when 

class ApplicationConfiguratorTest {

    static final String EXPECTED_REGISTRY_URL = 'http://my-reg'
    static final int EXPECTED_REGISTRY_INTERNAL_PORT = 0
    static final boolean EXPECTED_ARGOCD = false
    static final String EXPECTED_VAULT_MODE = 'prod'
    public static final String EXPECTED_JENKINS_URL = 'http://my-jenkins'
    public static final String EXPECTED_SCMM_URL = 'http://my-scmm'

    private ApplicationConfigurator applicationConfigurator
    private NetworkingUtils networkingUtils
    private FileSystemUtils fileSystemUtils
    private TestLogger testLogger
    Map testConfig = [
            registry   : [
                    url         : EXPECTED_REGISTRY_URL,
                    internalPort: EXPECTED_REGISTRY_INTERNAL_PORT,
                    path        : null
            ],
            jenkins    : [
                    url     : EXPECTED_JENKINS_URL
                    ],
            scmm       : [
                    url     : EXPECTED_SCMM_URL,
                    ],
            features    : [
                    argocd : [
                            active    : EXPECTED_ARGOCD
                    ],
                    secrets : [
                            vault : [
                                    mode : EXPECTED_VAULT_MODE
                            ]
                    ],
            ]
    ]

    @BeforeEach
    void setup() {
        networkingUtils = mock(NetworkingUtils.class)
        fileSystemUtils = mock(FileSystemUtils.class)
        applicationConfigurator = new ApplicationConfigurator(networkingUtils, fileSystemUtils, new SchemaValidator(new JsonSchemaGenerator()))
        testLogger = new TestLogger(applicationConfigurator.getClass())
        when(fileSystemUtils.getRootDir()).thenReturn("/test")
        when(fileSystemUtils.getLineFromFile("/test/scm-manager/values.yaml", "nodePort:")).thenReturn("nodePort: 9091")
        when(fileSystemUtils.getLineFromFile("/test/jenkins/values.yaml", "nodePort:")).thenReturn("nodePort: 9090")

        when(networkingUtils.createUrl(anyString(), anyString(), anyString())).thenCallRealMethod()
        when(networkingUtils.createUrl(anyString(), anyString())).thenCallRealMethod()
        when(networkingUtils.findClusterBindAddress()).thenReturn("localhost")
    }

    @Test
    void "correct config with no programm arguments"() {
        when(networkingUtils.findClusterBindAddress()).thenReturn("localhost")
        when(networkingUtils.createUrl("localhost", "9091", "/scm")).thenReturn("http://localhost:9091/scm")
        when(networkingUtils.getProtocol("http://localhost:9091/scm")).thenReturn("http")
        when(networkingUtils.getHost("http://localhost:9091/scm")).thenReturn("localhost:9091/scm")

        Map actualConfig = applicationConfigurator.setConfig(testConfig).getConfig()

        
        assertThat(actualConfig['registry']['internalPort']).isEqualTo(EXPECTED_REGISTRY_INTERNAL_PORT)
        assertThat(actualConfig['registry']['url']).isEqualTo(EXPECTED_REGISTRY_URL)
        assertThat(actualConfig['registry']['path']).isEqualTo('')
        assertThat(actualConfig['registry']['internal']).isEqualTo(false)
        
        assertThat(actualConfig['features']['argocd']['active']).isEqualTo(EXPECTED_ARGOCD)

        assertThat(actualConfig['jenkins']['url']).isEqualTo(EXPECTED_JENKINS_URL)
        assertThat(actualConfig['jenkins']['internal']).isEqualTo(false)
        
        assertThat(actualConfig['features']['secrets']['vault']['mode']).isEqualTo(EXPECTED_VAULT_MODE)
        // Default value
        assertThat(actualConfig['features']['secrets']['externalSecrets']).isNotNull()
        // Dynamic vaule (depends on vault mode)
        assertThat(actualConfig['features']['secrets']['active']).isEqualTo(true)
    }

    @Test
    void "uses k8s services for jenkins and scmm if running as k8s job"() {
        testConfig.jenkins['url'] = ''
        testConfig.scmm['url'] = ''

        withEnvironmentVariable("KUBERNETES_SERVICE_HOST", "127.0.0.1").execute {
            Map actualConfig = applicationConfigurator.setConfig(testConfig).getConfig()

            assertThat(actualConfig.scmm['url']).isEqualTo("http://scmm-scm-manager.default.svc.cluster.local:80/scm")
            assertThat(actualConfig.jenkins['url']).isEqualTo("http://jenkins.default.svc.cluster.local:80")
        }
    }

    @Test
    void "uses default localhost url for jenkins and scmm if nothing specified"() {
        testConfig.jenkins['url'] = ''
        testConfig.scmm['url'] = ''

        Map actualConfig = applicationConfigurator.setConfig(testConfig).getConfig()

        assertThat(actualConfig.scmm['url']).isEqualTo("http://localhost:9091/scm")
        assertThat(actualConfig.jenkins['url']).isEqualTo("http://localhost:9090")
    }

    /**
     * If you would like to run this test in the IDE, add the following JVM options. The same is done in pom.xml
     * --add-opens java.base/java.util=ALL-UNNAMED
     */
    @Test
    void "Certain properties are read from env"() {
        withEnvironmentVariable('SPRING_BOOT_HELM_CHART_REPO', 'value1').execute {
            Map actualConfig = new ApplicationConfigurator(networkingUtils, fileSystemUtils, new SchemaValidator(new JsonSchemaGenerator())).setConfig(testConfig).getConfig()
            assertThat(actualConfig['repositories']['springBootHelmChart']['url']).isEqualTo('value1')
        }
        withEnvironmentVariable('SPRING_PETCLINIC_REPO', 'value2').execute {
            Map actualConfig = new ApplicationConfigurator(networkingUtils, fileSystemUtils, new SchemaValidator(new JsonSchemaGenerator())).setConfig(testConfig).getConfig()
            assertThat(actualConfig['repositories']['springPetclinic']['url']).isEqualTo('value2')
        }
        withEnvironmentVariable('GITOPS_BUILD_LIB_REPO', 'value3').execute {
            Map actualConfig = new ApplicationConfigurator(networkingUtils, fileSystemUtils, new SchemaValidator(new JsonSchemaGenerator())).setConfig(testConfig).getConfig()
            assertThat(actualConfig['repositories']['gitopsBuildLib']['url']).isEqualTo('value3')
        }
        withEnvironmentVariable('CES_BUILD_LIB_REPO', 'value4').execute {
            Map actualConfig = new ApplicationConfigurator(networkingUtils, fileSystemUtils, new SchemaValidator(new JsonSchemaGenerator())).setConfig(testConfig).getConfig()
            assertThat(actualConfig['repositories']['cesBuildLib']['url']).isEqualTo('value4')
        }
    }

    @Test
    void 'cli overwrites config file'() {
        def configFile = File.createTempFile("gitops-playground", '.yaml')
        configFile.deleteOnExit()
        configFile.text = """
images:
  kubectl: "localhost:30000/kubectl"
  helm: "localhost:30000/helm"
        """
        applicationConfigurator
                .setConfig(configFile)
                .setConfig([
                        images: [
                                kubectl: null, // do not overwrite default value
                                helm: "localhost:30000/cli/helm",
                        ],
                ])

        assertThat(applicationConfigurator.config['images']['kubectl']).isEqualTo('localhost:30000/kubectl')
        assertThat(applicationConfigurator.config['images']['helm']).isEqualTo('localhost:30000/cli/helm')
    }

    @Test
    void "config is deeply immutable"() {
        // Avoids failing due to compile static 🤷‍♂️
        ApplicationConfigurator configurator = applicationConfigurator
        shouldFail(UnsupportedOperationException) {
            configurator.config['application']['remote'] = true
        }
        shouldFail(UnsupportedOperationException) {
            configurator.setConfig(testConfig).getConfig()['application']['remote'] = true
        }
    }
}
