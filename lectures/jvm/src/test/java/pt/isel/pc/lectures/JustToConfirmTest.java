package pt.isel.pc.lectures;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JustToConfirmTest {

    private static Logger logger = LoggerFactory.getLogger(JustToConfirmTest.class);

    @Test
    public void everythingIsOk(){
        logger.info("Yes, everything is ok");
    }
}
