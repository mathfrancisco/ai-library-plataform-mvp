package com.ailibrary.dashboard;
import com.ailibrary.common.security.CurrentUser;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/dashboard")
public class DashboardController {
  private final DashboardService dashboard; private final CurrentUser currentUser;
  public DashboardController(DashboardService d, CurrentUser currentUser){this.dashboard=d;this.currentUser=currentUser;}
  @GetMapping public DashboardService.Dashboard get(){return dashboard.get(currentUser.id());}
}
