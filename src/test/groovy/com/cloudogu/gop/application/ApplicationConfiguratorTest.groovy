package com.cloudogu.gop.application

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import com.cloudogu.gop.application.utils.FileSystemUtils
import com.cloudogu.gop.application.utils.NetworkingUtils
import com.cloudogu.gop.utils.GopTestConfig
import com.cloudogu.gop.utils.TestLogger
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.mockito.Mockito.*
import static org.junit.jupiter.api.Assertions.*
import static groovy.json.JsonOutput.prettyPrint
import static groovy.json.JsonOutput.toJson

class ApplicationConfiguratorTest {

    private ApplicationConfigurator applicationConfigurator
    private NetworkingUtils networkingUtils
    private FileSystemUtils fileSystemUtils
    private TestLogger testLogger

    @BeforeEach
    void setup() {
        networkingUtils = mock(NetworkingUtils.class)
        fileSystemUtils = mock(FileSystemUtils.class)
        applicationConfigurator = new ApplicationConfigurator(GopTestConfig.get(), networkingUtils, fileSystemUtils)
        testLogger = new TestLogger(applicationConfigurator.getClass())
        when(fileSystemUtils.getGopRoot()).thenReturn("/test")
        when(fileSystemUtils.getLineFromFile("/test/scm-manager/values.yaml", "nodePort:")).thenReturn("nodePort: 9091")

    }

    @Test
    void "correct config with no programm arguments"() {
        when(networkingUtils.findClusterBindAddress()).thenReturn("localhost")
        when(networkingUtils.createUrl("localhost", "9091", "/scm")).thenReturn("http://localhost:9091/scm")
        when(networkingUtils.getProtocol("http://localhost:9091/scm")).thenReturn("http")
        when(networkingUtils.getHost("http://localhost:9091/scm")).thenReturn("localhost:9091/scm")

        applicationConfigurator.populateConfig()

        List<ILoggingEvent> foundLog = testLogger.getLogs().search("\"registry\":")

        assertTrue(foundLog.toString().contains("\"url\": \"http://localhost:9091/scm\","))
        assertTrue(foundLog.toString().contains("\"username\": \"admin\","))
        assertTrue(foundLog.toString().contains("\"password\": \"admin\","))
        assertTrue(foundLog.toString().contains("\"internal\": true,"))
        assertTrue(foundLog.toString().contains("\"urlForJenkins\": \"http://scmm-scm-manager/scm\","))
        assertTrue(foundLog.toString().contains("\"host\": \"localhost:9091/scm\","))
        assertTrue(foundLog.toString().contains("\"protocol\": \"http\""))
    }
}
