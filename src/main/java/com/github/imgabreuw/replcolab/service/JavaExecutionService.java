package com.github.imgabreuw.replcolab.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class JavaExecutionService {

    public String execute(String code) {
        Path codeDir = null;
        File javaFile = null;
        File classFile = null;

        try {
            codeDir = Files.createDirectories(Path.of("runtime"));
            codeDir.toFile().deleteOnExit();

            String className = "Main";
            javaFile = codeDir.resolve(className + ".java").toFile();
            classFile = codeDir.resolve(className + ".class").toFile();

            try (FileWriter writer = new FileWriter(javaFile)) {
                writer.write(code);
            }

            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                return "Erro: Compilador Java não disponível. Verifique se está usando JDK e não JRE.";
            }

            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
                Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(List.of(javaFile));

                JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, List.of("-d", codeDir.toString()), null, compilationUnits);

                boolean success = task.call();

                if (!success) {
                    StringBuilder errorMsg = new StringBuilder("Erro de compilação:\n");
                    diagnostics
                            .getDiagnostics()
                            .forEach(diagnostic -> errorMsg.append(String.format("Linha %d: %s\n", diagnostic.getLineNumber(), diagnostic.getMessage(null))));
                    return errorMsg.toString();
                }
            }

            ProcessBuilder runBuilder = new ProcessBuilder("java", "-cp", codeDir.toString(), className);
            Process runProcess = runBuilder.start();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            StreamUtils.copy(runProcess.getInputStream(), output);

            ByteArrayOutputStream error = new ByteArrayOutputStream();
            StreamUtils.copy(runProcess.getErrorStream(), error);

            runProcess.waitFor(5, TimeUnit.SECONDS);

            String result = output.toString();
            if (error.size() > 0) {
                result += "\n--- Erros de execução ---\n" + error.toString();
            }

            return result;
        } catch (Exception e) {
            return "Erro: " + e.getMessage();
        } finally {
            // Limpar arquivos
            if (javaFile != null) javaFile.delete();
            if (classFile != null) classFile.delete();
            if (codeDir != null && codeDir.toFile().exists()) {
                codeDir.toFile().delete();
            }
        }
    }
}