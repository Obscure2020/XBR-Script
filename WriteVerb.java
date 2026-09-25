import java.util.HashSet;

public class WriteVerb implements ProcedureVerb {

    private final String read_variable;
    private final String write_target;

    public WriteVerb(long human_line, String[] chunks, HashSet<String> virtual_context) throws Exception {
        if(chunks.length != 3){
            throw new ProcedureParseException("On line " + human_line + ": Invalid number of arguments for a write operation.");
        }
        read_variable = chunks[1];
        write_target = chunks[2];
        if(!virtual_context.contains(read_variable)){
            throw new ProcedureParseException("On line " + human_line + ": Variable \"" + read_variable + "\" doesn't seem to have been created before this point.");
        }
    }

    public void Execute(ProcedureContext context) throws Exception {
        System.out.println(Main.ANSI_BRIGHT_RED + "WriteVerb currently has no implemented execution!" + Main.ANSI_RESET);
    }

}