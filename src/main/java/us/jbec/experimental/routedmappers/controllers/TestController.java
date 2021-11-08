package us.jbec.experimental.routedmappers.controllers;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import us.jbec.experimental.routedmappers.models.RoutedMapperTarget;
import us.jbec.experimental.routedmappers.models.RoutedMapperTargetContextHolder;
import us.jbec.experimental.routedmappers.services.TransactionTestService;

import java.lang.reflect.UndeclaredThrowableException;


@RestController
public class TestController {

    private final TransactionTestService transactionTestService;

    public TestController(TransactionTestService transactionTestService) {
        this.transactionTestService = transactionTestService;
    }

    @GetMapping("/random")
    public void insertRandom() {
        var id = (int) Math.round(Math.random() * 10000);
        transactionTestService.testInsert(id);
        transactionTestService.verifyInsert(id);
    }

    @GetMapping("/rollback")
    public void testRollback() {
        var id = (int) Math.round(Math.random() * 10000);
        try {
            transactionTestService.testInsertMultipleMappersRollback(id);
            throw new RuntimeException("Rollback did not occur!");
        } catch (UndeclaredThrowableException e) {
            transactionTestService.verifyInsertMultipleMappersRollback(id);
        }
    }

    // method itself is transactional
    @Transactional(timeout = 600)
    @GetMapping("/prop")
    public void testPropagationRollback() {
        RoutedMapperTargetContextHolder.setRoutedMapperTarget(RoutedMapperTarget.DB1);
        var id1 = (int) Math.round(Math.random() * 10000);
        var id2 = (int) Math.round(Math.random() * 10000);
        var id3 = (int) Math.round(Math.random() * 10000);

        transactionTestService.testInsert(id1);
        transactionTestService.testInsertNoTransaction(id2);
        transactionTestService.testInsert(id3);
        // duplicate key
        transactionTestService.testInsert(id3);
    }

}
