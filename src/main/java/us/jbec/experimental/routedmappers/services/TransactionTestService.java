package us.jbec.experimental.routedmappers.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import us.jbec.experimental.routedmappers.mappers.MapperOne;
import us.jbec.experimental.routedmappers.mappers.MapperTwo;
import us.jbec.experimental.routedmappers.models.MessagePayload;
import us.jbec.experimental.routedmappers.models.RoutedMapperTarget;
import us.jbec.experimental.routedmappers.models.RoutedMapperTargetContextHolder;

@Service
public class TransactionTestService {
    private final MapperOne mapperOne;
    private final MapperTwo mapperTwo;

    private static Logger LOGGER = LoggerFactory.getLogger(TransactionTestService.class);

    public TransactionTestService(MapperOne mapperOne, MapperTwo mapperTwo) {
        this.mapperOne = mapperOne;
        this.mapperTwo = mapperTwo;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void testInsertNoTransaction(int id) {
        LOGGER.info("Check database for entry (no tx): {}", id);
        testInsert(id);
    }

    @Transactional
    public void testInsert(int id) {
        LOGGER.info("Check database for entry: {}", id);
        RoutedMapperTargetContextHolder.setRoutedMapperTarget(RoutedMapperTarget.DB1);
        mapperOne.insertDataWithProc(new MessagePayload(id, "database1"));
        RoutedMapperTargetContextHolder.setRoutedMapperTarget(RoutedMapperTarget.DB2);
        mapperOne.insertDataWithProc(new MessagePayload(id, "database2"));
    }

    public void verifyInsert(int id) {
        RoutedMapperTargetContextHolder.setRoutedMapperTarget(RoutedMapperTarget.DB1);
        var data1 = mapperOne.selectDateFromProc(id);
        RoutedMapperTargetContextHolder.setRoutedMapperTarget(RoutedMapperTarget.DB2);
        var data2 = mapperOne.selectDateFromProc(id);

        Assert.notNull(data1, "Data was not inserted into db1!");
        Assert.isTrue(data1.getMessage().equals("database1"));

        Assert.notNull(data2, "Data was not inserted into db2!");
        Assert.isTrue(data2.getMessage().equals("database2"));
    }

    public void verifyNotInsert(int id) {
        RoutedMapperTargetContextHolder.setRoutedMapperTarget(RoutedMapperTarget.DB1);
        var data1 = mapperOne.selectDateFromProc(id);
        RoutedMapperTargetContextHolder.setRoutedMapperTarget(RoutedMapperTarget.DB2);
        var data2 = mapperOne.selectDateFromProc(id);

        Assert.isNull(data1, "Data was not inserted into db1!");

        Assert.isNull(data2, "Data was not inserted into db2!");
    }

    @Transactional
    public void testInsertMultipleMappersRollback(int id) {
        LOGGER.info("Attempt insert for entry: {}", id);

        RoutedMapperTargetContextHolder.setRoutedMapperTarget(RoutedMapperTarget.DB1);
        mapperOne.insertDataWithProc(new MessagePayload(id, "database1"));

        RoutedMapperTargetContextHolder.setRoutedMapperTarget(RoutedMapperTarget.DB2);
        mapperOne.insertDataWithProc(new MessagePayload(id, "database2"));

        RoutedMapperTargetContextHolder.setRoutedMapperTarget(RoutedMapperTarget.DB1);
        mapperTwo.insertDataWithProc(new MessagePayload(id, "database1"));

        RoutedMapperTargetContextHolder.setRoutedMapperTarget(RoutedMapperTarget.DB2);
        mapperTwo.insertDataWithProc(new MessagePayload(id, "database2"));

//         Duplicate Key Exception
        mapperTwo.insertDataWithProc(new MessagePayload(id, "database2"));
    }

    public void verifyInsertMultipleMappersRollback(int id) {
        RoutedMapperTargetContextHolder.setRoutedMapperTarget(RoutedMapperTarget.DB2);
        Assert.isNull(mapperOne.selectDateFromProc(id));
        Assert.isNull(mapperTwo.selectDateFromProc(id));
        RoutedMapperTargetContextHolder.setRoutedMapperTarget(RoutedMapperTarget.DB1);
        Assert.isNull(mapperOne.selectDateFromProc(id));
        Assert.isNull(mapperTwo.selectDateFromProc(id));
    }

}
