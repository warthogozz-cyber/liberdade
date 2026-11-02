package br.com.liberdadefamilia;

import org.springframework.boot.SpringApplication;

public class TestLiberdadeApplication {

    public static void main(String[] args) {
        SpringApplication.from(LiberdadeApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
