package de.example.reports.gui;

import de.example.reports.model.ReportStatus;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import java.util.*;

/** Holder metadata for the staff report list and detail inventories. */
public final class AdminReportInventory implements InventoryHolder {
 public enum View { LIST, DETAIL }
 private Inventory inventory; private final View view; private final int page; private final ReportStatus filter; private final List<Long> reportIds; private final long reportId; private final int tempBanIndex,tempMuteIndex;
 private AdminReportInventory(View view,int page,ReportStatus filter,List<Long> reportIds,long reportId,int tempBanIndex,int tempMuteIndex){this.view=view;this.page=page;this.filter=filter;this.reportIds=List.copyOf(reportIds);this.reportId=reportId;this.tempBanIndex=tempBanIndex;this.tempMuteIndex=tempMuteIndex;}
 public static AdminReportInventory list(int page,ReportStatus filter,List<Long> ids){return new AdminReportInventory(View.LIST,page,filter,ids,-1,2,2);}
 public static AdminReportInventory detail(long id,int page,ReportStatus filter){return detail(id,page,filter,2,2);}
 public static AdminReportInventory detail(long id,int page,ReportStatus filter,int banIndex,int muteIndex){return new AdminReportInventory(View.DETAIL,page,filter,List.of(),id,banIndex,muteIndex);}
 public void inventory(Inventory value){inventory=value;} @Override public @NotNull Inventory getInventory(){return inventory;}
 public View view(){return view;} public int page(){return page;} public ReportStatus filter(){return filter;} public List<Long> reportIds(){return reportIds;} public long reportId(){return reportId;} public int tempBanIndex(){return tempBanIndex;} public int tempMuteIndex(){return tempMuteIndex;}
}
