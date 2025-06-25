package org.test;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

import java.time.Duration;

public class OllamaDockerRunner {

    public static void main(String[] args) {
        try {
            // 1. Конфигурация подключения к Docker
            DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                    .build();

            // 2. Создание HTTP-клиента
            DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                    .dockerHost(config.getDockerHost())
                    .maxConnections(100)
                    .connectionTimeout(Duration.ofSeconds(30))
                    .responseTimeout(Duration.ofSeconds(45))
                    .build();

            // 3. Создание Docker-клиента
            DockerClient dockerClient = DockerClientImpl.getInstance(config, httpClient);

            // Имя образа Ollama
            String imageName = "ollama/ollama:latest";

            System.out.println("Pulling Ollama image...");
            // Скачиваем образ (если его нет локально)
            dockerClient.pullImageCmd(imageName)
                    .exec(new PullImageResultCallback())
                    .awaitCompletion();

            System.out.println("Creating Ollama container...");
            // Создаем контейнер
            CreateContainerResponse container = dockerClient.createContainerCmd(imageName)
                    .withName("ollama-java")  // Имя контейнера
                    .withExposedPorts(new ExposedPort(11434))
//                    .withVolumes(new Volume("/root/.ollama"))  // Исправленный способ задания volume
//                    .withVolumes(new Volume("./ollama_data:/root/.ollama"))
                    .withHostConfig(
                            new HostConfig()
                                    .withPortBindings(
                                            new Ports(new ExposedPort(11434), Ports.Binding.bindPort(11434))
                                    )
//                                    .withBinds(Collections.singletonList(
//                                            Bind.parse("./ollama_data:/root/.ollama")
//////                                            "./ollama_data:/root/.ollama"  // Правильное задание bind mount
//                                    ))
//                                    .withBinds(
//                                            // Формат: Bind(путь_на_хосте, Volume(путь_в_контейнере))
////                                            new Bind("./ollama_data", new Volume("/root/.ollama"))
//                                            new Bind("ollama_data", new Volume("/root/.ollama"))
//                                    )
                                    .withAutoRemove(true)  // Автоудаление при остановке
                    )
                    .exec();

            System.out.println("Starting container...");
            // Запускаем контейнер
            dockerClient.startContainerCmd(container.getId()).exec();

            // Выполняем команду внутри контейнера
            System.out.println("Pulling model qwen3:0.6b...");
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(container.getId())
                    .withCmd("ollama", "pull", "qwen3:0.6b")
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .exec();

            dockerClient.execStartCmd(execCreateCmdResponse.getId())
                    .exec(new LogContainerResultCallback() {
                        @Override
                        public void onNext(Frame item) {
                            System.out.println("[CONTAINER LOG COMMAND] " + item.toString());
                        }
                    })
                    .awaitCompletion();

            // Запускаем поток для чтения логов
            new Thread(() -> {
                try {
                    dockerClient.logContainerCmd(container.getId())
                            .withStdOut(true)
                            .withStdErr(true)
                            .withFollowStream(true)
                            .exec(new LogContainerResultCallback() {
                                @Override
                                public void onNext(Frame item) {
                                    System.out.println("[CONTAINER LOG] " + item.toString());
                                }
                            }).awaitCompletion();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("Log reading interrupted");
                }
            }).start();

            System.out.println("Ollama is running!");
            System.out.println("Access it at: http://localhost:11434");
            System.out.println("Use Ctrl+C to stop...");

            // Добавляем обработчик для graceful shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nStopping container...");
                try {
                    dockerClient.stopContainerCmd(container.getId()).exec();
                    System.out.println("Container stopped successfully.");
                } catch (Exception e) {
                    System.err.println("Error stopping container: " + e.getMessage());
                }
            }));

            // Бесконечное ожидание с возможностью прерывания
            while (true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}