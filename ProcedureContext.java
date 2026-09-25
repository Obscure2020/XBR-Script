import java.awt.image.BufferedImage;
import java.util.HashMap;

public class ProcedureContext {

    public final HashMap<String, BufferedImage> variables;

    public ProcedureContext(){
        variables = new HashMap<>();
    }

}