import java.util.HashSet;

public class ReadVerb implements ProcedureVerb {

    private final String read_target;
    private final String write_variable;

    public ReadVerb(long human_line, String[] chunks, HashSet<String> virtual_context) throws Exception {
        if(chunks.length != 3){
            throw new ProcedureParseException("On line " + human_line + ": Invalid number of arguments for a read operation.");
        }
        read_target = chunks[1];
        write_variable = chunks[2];
        if(!Main.checkInputRelativeExists(read_target)){
            throw new ProcedureParseException("On line " + human_line + ": Input path \"" + read_target + "\" doesn't seem to exist.");
        }
        virtual_context.add(write_variable);
    }

}