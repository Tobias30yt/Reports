package de.example.reports.api;
import java.sql.*;
/** SQLite triggers keep local moderation and the sync queue in the same transaction. */
public final class OutboxSchema {
    public static void install(Connection c) throws SQLException {
        try(Statement s=c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS api_outbox(sequence INTEGER PRIMARY KEY AUTOINCREMENT,entity TEXT NOT NULL,record_id TEXT NOT NULL,data TEXT)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS api_meta(id INTEGER PRIMARY KEY CHECK(id=1))");
            String[] tables={"sanctions","reports"};
            for(String table:tables){
                String entity=table.equals("sanctions")?"sanction":"report";
                for(String operation:new String[]{"INSERT","UPDATE","DELETE"}){
                    String ref=operation.equals("DELETE")?"OLD":"NEW";
                    String id=table.equals("sanctions")?ref+".player_uuid||':'||"+ref+".type":"CAST("+ref+".id AS TEXT)";
                    String data=operation.equals("DELETE")?"NULL":json(table,ref);
                    s.executeUpdate("CREATE TRIGGER IF NOT EXISTS api_"+table+"_"+operation+" AFTER "+operation+" ON "+table+" BEGIN INSERT INTO api_outbox(entity,record_id,data) VALUES('"+entity+"',"+id+","+data+"); END");
                }
            }
            try(ResultSet r=s.executeQuery("SELECT count(*) FROM api_meta")){
                if(r.next()&&r.getInt(1)>0)return;
            }
            s.executeUpdate("INSERT INTO api_outbox(entity,record_id,data) SELECT 'sanction',player_uuid||':'||type,"+json("sanctions","sanctions")+" FROM sanctions");
            s.executeUpdate("INSERT INTO api_outbox(entity,record_id,data) SELECT 'report',CAST(id AS TEXT),"+json("reports","reports")+" FROM reports");
            s.executeUpdate("INSERT INTO api_meta VALUES(1)");
        }
    }
    private static String json(String table,String ref){
        String[] fields=table.equals("sanctions")?new String[]{"player_uuid","player_name","type","expires_at","reason"}:new String[]{"id","reported_player_uuid","reported_player_name","reporter_uuid","reporter_name","reason","timestamp","status","grim_flags"};
        return "json_object("+String.join(",",java.util.Arrays.stream(fields).map(f->"'"+f+"',"+ref+"."+f).toList())+")";
    }
}
