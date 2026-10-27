import de.example.reports.database.Database;
import de.example.reports.model.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
public class OutboxCheck {
 public static void main(String[] args)throws Exception{
  Path path=Files.createTempDirectory("reports-outbox-test").resolve("reports.db");UUID target=UUID.randomUUID(),actor=UUID.randomUUID();
  long acknowledged;
  try(Database db=new Database(path.toFile())){
   db.sanction(target,"Test",Sanction.Type.MUTE,Instant.now().plusSeconds(60),"Quotes \" and ünicode").get();
   var first=db.apiBatch().get();if(first.size()!=1||!first.getFirst().json().contains("Quotes \\\" and ünicode"))throw new AssertionError("Sanction not durably queued / escaped");
   db.create(target,"Test",actor,"Reporter","A report","No flags").get();
   db.removeSanction(target,Sanction.Type.MUTE).get();
   var events=db.apiBatch().get();if(events.size()!=3||!events.getLast().json().contains("\"data\":null"))throw new AssertionError("Report or tombstone missing");
   acknowledged=events.getFirst().sequence();db.apiAcknowledge(acknowledged).get();
  }
  try(Database db=new Database(path.toFile())){
   var events=db.apiBatch().get();if(events.size()!=2||events.getFirst().sequence()<=acknowledged)throw new AssertionError("Restart lost queue or re-seeded records");
   db.apiAcknowledge(events.getLast().sequence()).get();if(!db.apiBatch().get().isEmpty())throw new AssertionError("Acknowledgment failed");
   db.sanction(target,"Test",Sanction.Type.MUTE,Instant.now().minusSeconds(1),"Expired").get();
   if(db.sanction(target,Sanction.Type.MUTE).get().isPresent())throw new AssertionError("Expired mute enforced");
   if(db.apiBatch().get().size()!=2)throw new AssertionError("Expiry deletion not queued");
  }
  System.out.println("PASS: durable outbox, JSON escaping, report events, removals, acknowledgment, restart, expiry.");
 }
}
