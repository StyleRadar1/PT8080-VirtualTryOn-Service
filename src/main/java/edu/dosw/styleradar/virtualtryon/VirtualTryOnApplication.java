package edu.dosw.styleradar.virtualtryon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Clase principal de arranque del microservicio PT8080-VirtualTryOn-Service.
 * Proyecto StyleRadar - Squad Portal8080 (DOSW 2026).
 */
@SpringBootApplication
@EnableScheduling
public class VirtualTryOnApplication {

    public static void main(String[] args) {
        SpringApplication.run(VirtualTryOnApplication.class, args);
    }

}
