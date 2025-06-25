package org.test;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class OllamaDockerRunner {

    public static void main(String[] args) throws InterruptedException {
        // 1. Конфигурация подключения к Docker
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
//                .withDockerHost("unix:///var/run/docker.sock") // Linux/Mac
//                .withDockerHost("tcp://localhost:2375") // Windows
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
                .withHostConfig(
                        new HostConfig()
                                .withPortBindings(
                                        new Ports(new ExposedPort(11434), Ports.Binding.bindPort(11434))
                                )
                                .withAutoRemove(true)  // Автоудаление при остановке
                )
                .exec();

        System.out.println("Starting container...");
        // Запускаем контейнер
        dockerClient.startContainerCmd(container.getId()).exec();

        System.out.println("Ollama is running!");
        System.out.println("Access it at: http://localhost:11434");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            dockerClient.stopContainerCmd(container.getId()).exec();
            System.out.println("Контейнер остановлен.");
        }));

        // Ожидание (можно заменить на ожидание логов и т.д.)
        Thread.sleep(60000);  // 1 минута

        // Остановка контейнера (опционально)
        System.out.println("Stopping container...");
        dockerClient.stopContainerCmd(container.getId()).exec();
    }
}