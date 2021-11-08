package us.jbec.experimental.routedmappers.mappers;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;
import us.jbec.experimental.routedmappers.models.MessagePayload;

@Repository
public interface MapperTwo {
    @Insert("CALL INSERT_DATA_2(#{id, mode=IN, jdbcType=SMALLINT},#{message, mode=IN, jdbcType=VARCHAR});")
    void insertDataWithProc(MessagePayload data);

    @Select("CALL SELECT_DATA_2(#{id, mode=IN, jdbcType=SMALLINT});")
    MessagePayload selectDateFromProc(int key);
}
