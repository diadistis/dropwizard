package io.dropwizard.logging;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.util.StatusPrinter;
import com.codahale.metrics.MetricRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class DefaultLoggingFactoryPrintErrorMessagesTest {
    @SuppressWarnings("NullAway")
    @TempDir
    static Path tempDir;

    private DefaultLoggingFactory factory;
    private ByteArrayOutputStream output;

    @BeforeEach
    public void setUp() throws Exception {
        output = new ByteArrayOutputStream();
        factory = new DefaultLoggingFactory(new LoggerContext(), new PrintStream(output));
    }

    @AfterEach
    public void tearDown() throws Exception {
        factory.stop();
    }

    private void configureLoggingFactoryWithFileAppender(File file) {
        factory.setAppenders(singletonList(newFileAppenderFactory(file)));
    }

    private AppenderFactory<ILoggingEvent> newFileAppenderFactory(File file) {
        FileAppenderFactory<ILoggingEvent> fileAppenderFactory = new FileAppenderFactory<>();

        fileAppenderFactory.setCurrentLogFilename(file.toString() + File.separator + "my-log-file.log");
        fileAppenderFactory.setArchive(false);

        return fileAppenderFactory;
    }

    private String configureAndGetOutputWrittenToErrorStream() throws UnsupportedEncodingException {
        factory.configure(new MetricRegistry(), "logger-test");

        return output.toString(StandardCharsets.UTF_8.name());
    }

    @Test
    public void testWhenUsingDefaultConstructor_SystemErrIsSet() throws Exception {
        PrintStream configurationErrorsStream = new DefaultLoggingFactory().getConfigurationErrorsStream();

        assertThat(configurationErrorsStream).isSameAs(System.err);
    }

    @Test
    public void testWhenUsingDefaultConstructor_StaticILoggerFactoryIsSet() throws Exception {
        LoggerContext loggerContext = new DefaultLoggingFactory().getLoggerContext();

        assertThat(loggerContext).isSameAs(LoggerFactory.getILoggerFactory());
    }

    @Test
    public void testWhenFileAppenderDoesNotHaveWritePermissionToFolder_PrintsErrorMessageToConsole() throws Exception {
        File folderWithoutWritePermission = Files.createFile(tempDir.resolve("folder-without-write-permission")).toFile();
        assumeTrue(folderWithoutWritePermission.setWritable(false));

        configureLoggingFactoryWithFileAppender(folderWithoutWritePermission);

        assertThat(folderWithoutWritePermission.canWrite()).isFalse();
        assertThat(configureAndGetOutputWrittenToErrorStream()).contains(folderWithoutWritePermission.toString());
    }

    @Test
    public void testWhenSettingUpLoggingWithValidConfiguration_NoErrorMessageIsPrintedToConsole() throws Exception {
        File folderWithWritePermission = Files.createDirectory(tempDir.resolve("folder-with-write-permission")).toFile();

        configureLoggingFactoryWithFileAppender(folderWithWritePermission);

        assertThat(folderWithWritePermission.canWrite()).isTrue();
        assertThat(configureAndGetOutputWrittenToErrorStream()).isEmpty();
    }

    @Test
    public void testLogbackStatusPrinterPrintStreamIsRestoredToSystemOut() throws Exception {
        Field field = StatusPrinter.class.getDeclaredField("ps");
        field.setAccessible(true);

        PrintStream out = (PrintStream) field.get(null);
        assertThat(out).isSameAs(System.out);
    }
}
