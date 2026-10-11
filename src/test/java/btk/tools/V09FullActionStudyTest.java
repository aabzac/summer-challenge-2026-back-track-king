package btk.tools;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class V09FullActionStudyTest {
    @Test void fixedActionCheckIncludesCellOrderAndTarget() {
        var expected=JsonParser.parseString("{\"cells\":[2,3],\"target\":-1,\"action\":\"PLACE_TRACKS 2 0;PLACE_TRACKS 3 0\"}").getAsJsonObject();
        var exact=new V09ContinuationProbe.Plan(new int[]{2,3},-1,"PLACE_TRACKS 2 0;PLACE_TRACKS 3 0");
        assertDoesNotThrow(()->V09FullActionStudy.requireSameAction(exact,expected));
        assertThrows(IllegalStateException.class,()->V09FullActionStudy.requireSameAction(
                new V09ContinuationProbe.Plan(new int[]{3,2},-1,exact.action()),expected));
        assertThrows(IllegalStateException.class,()->V09FullActionStudy.requireSameAction(
                new V09ContinuationProbe.Plan(exact.cells(),7,exact.action()),expected));
    }
}
