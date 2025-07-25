package sh4re_v2.sh4re_v2.repository.main;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sh4re_v2.sh4re_v2.domain.main.School;

@Repository
public interface SchoolRepository extends JpaRepository<School, Long> {
    Optional<School> findByCode(String username);
}