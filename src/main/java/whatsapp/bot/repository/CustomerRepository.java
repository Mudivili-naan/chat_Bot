package whatsapp.bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import whatsapp.bot.entity.Customer;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, String> {
}
