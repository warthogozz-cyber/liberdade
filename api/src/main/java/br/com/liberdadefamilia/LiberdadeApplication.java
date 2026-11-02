package br.com.liberdadefamilia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@SpringBootApplication
@Modulithic(sharedModules = "shared")
public class LiberdadeApplication {

    public static void main(String[] args) {
        SpringApplication.run(LiberdadeApplication.class, args);
    }
}
