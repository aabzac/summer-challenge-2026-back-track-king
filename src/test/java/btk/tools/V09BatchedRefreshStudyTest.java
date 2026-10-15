package btk.tools;

import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class V09BatchedRefreshStudyTest {
    @TempDir Path temp;

    @Test void floatingPointBitsAndArrayContentsAreComparedExactly() {
        assertFalse(V09BatchedRefreshStudy.semanticEquals(0.0,-0.0));
        assertFalse(V09BatchedRefreshStudy.semanticEquals(new Double[]{null,0.0},new Double[]{null,-0.0}));
        assertTrue(V09BatchedRefreshStudy.semanticEquals(new int[][]{{1,2},{3}},new int[][]{{1,2},{3}}));
        assertFalse(V09BatchedRefreshStudy.semanticEquals(new int[][]{{1,2},{3}},new int[][]{{1,2},{4}}));
    }

    @Test void commonStrategyDiagnosticsAndOrderedActionsMustMatch() {
        var a=new V09BatchedRefreshStudy.Decision("PLACE_TRACKS 1 0;DISRUPT 2",new int[]{1},2,9,
                Double.doubleToRawLongBits(0.0),true,Map.of("simulatedTurns",8,"selectedValue",0.0,"deadlineStatus","disabled"));
        var extra=new V09BatchedRefreshStudy.Decision(a.action(),a.cells(),2,1,a.selectedValueRawBits(),true,
                Map.of("simulatedTurns",8,"selectedValue",0.0,"addedDisabledField",false));
        assertTrue(V09BatchedRefreshStudy.differences(a,extra).isEmpty());
        var changed=new V09BatchedRefreshStudy.Decision("DISRUPT 2;PLACE_TRACKS 1 0",new int[]{1},2,1,
                Double.doubleToRawLongBits(-0.0),true,Map.of("simulatedTurns",7,"selectedValue",-0.0));
        var differences=V09BatchedRefreshStudy.differences(a,changed);
        assertTrue(differences.contains("orderedAction"));assertTrue(differences.contains("selectedValueRawBits"));
        assertTrue(differences.contains("selectedValue"));assertTrue(differences.contains("simulatedTurns"));
    }

    @Test void ampleCompletionRequiresBothSearchPhasesExceptTheOpeningReturn() {
        Map<String,Object> diagnostics=new LinkedHashMap<>();
        diagnostics.put("ordinarySearchComplete",true);diagnostics.put("networkPhaseComplete",false);
        assertFalse(V09BatchedRefreshStudy.completeSearch(diagnostics));
        diagnostics.put("networkPhaseComplete",true);assertTrue(V09BatchedRefreshStudy.completeSearch(diagnostics));
        diagnostics.put("ordinarySearchComplete",false);assertFalse(V09BatchedRefreshStudy.completeSearch(diagnostics));
        diagnostics.put("selectedOpening",true);assertTrue(V09BatchedRefreshStudy.completeSearch(diagnostics));
    }

    @Test void sourceAndFullOffOnConfigurationCannotDrift()throws Exception {
        Path source=temp.resolve("Player.java");Files.writeString(source,"retained exact source");
        JsonObject manifest=new JsonObject();manifest.addProperty("sha256",V09ContinuationStudy.hash(source));
        assertDoesNotThrow(()->V09BatchedRefreshStudy.verifyManifestSource(manifest,source));
        Files.writeString(source,"changed source");
        assertThrows(IllegalStateException.class,()->V09BatchedRefreshStudy.verifyManifestSource(manifest,source));
        Map<String,String> off=Map.of("NOISE","0.1","USE_BATCHED_REFRESH","false","SEED","1");
        Map<String,String> on=Map.of("NOISE","0.1","USE_BATCHED_REFRESH","true","SEED","1");
        assertTrue(V09BatchedRefreshStudy.sameOffOnParameters(off,on));
        assertFalse(V09BatchedRefreshStudy.sameOffOnParameters(off,Map.of("NOISE","0.1","USE_BATCHED_REFRESH","true")));
        assertFalse(V09BatchedRefreshStudy.sameOffOnParameters(off,Map.of("NOISE","0.0","USE_BATCHED_REFRESH","true","SEED","1")));
    }
}
