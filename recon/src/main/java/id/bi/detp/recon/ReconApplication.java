package id.bi.detp.recon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
public class ReconApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReconApplication.class, args);
    }
}
