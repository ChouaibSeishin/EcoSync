package io.ecosync.database;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class DatabasePolicyTest {
 @Test void exactOperatorTuplesCannotBeExpandedByTenant(){var p=new DatabasePolicy("POSTGRESQL|sales|5432|sales|sales|dev","development");p.check("POSTGRESQL","sales",5432,"sales",List.of("sales"),false);for(String host:List.of("169.254.169.254","sales.evil","sales?password=x","SALES","sales."))assertThatThrownBy(()->p.check("POSTGRESQL",host,5432,"sales",List.of("sales"),false)).isInstanceOf(DatabaseFailure.class);assertThatThrownBy(()->p.check("POSTGRESQL","sales",5433,"sales",List.of("sales"),false)).isInstanceOf(DatabaseFailure.class);assertThatThrownBy(()->p.check("POSTGRESQL","sales",5432,"sales",List.of("sales","pg_catalog"),false)).isInstanceOf(DatabaseFailure.class);}
 @Test void productionCannotUsePlaintextEvenWhenListed(){var p=new DatabasePolicy("MYSQL|inventory|3306|inventory|inventory|dev","production");assertThatThrownBy(()->p.check("MYSQL","inventory",3306,"inventory",List.of("inventory"),false)).isInstanceOf(DatabaseFailure.class);}
}
