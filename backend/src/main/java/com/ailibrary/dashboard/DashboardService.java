package com.ailibrary.dashboard;

import com.ailibrary.library.domain.LibraryStatus;
import com.ailibrary.library.repository.UserLibraryRepository;
import com.ailibrary.reading.repository.ReadingProgressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class DashboardService {
    private final UserLibraryRepository library; private final ReadingProgressRepository progress;
    public DashboardService(UserLibraryRepository library, ReadingProgressRepository progress){this.library=library;this.progress=progress;}
    public record Dashboard(long totalBooks,long wantToRead,long reading,long read,long dropped,long pagesTracked,double averageProgress){}
    @Transactional(readOnly=true)
    public Dashboard get(UUID userId){
        var rows=progress.findByUserId(userId);
        long pages=rows.stream().mapToLong(r->r.getCurrentPage()).sum();
        double avg=rows.stream().mapToDouble(r->r.getPercentage()==null?0:r.getPercentage().doubleValue()).average().orElse(0);
        return new Dashboard(library.countByUserId(userId), library.countByUserIdAndStatus(userId, LibraryStatus.WANT_TO_READ),
          library.countByUserIdAndStatus(userId, LibraryStatus.READING),library.countByUserIdAndStatus(userId, LibraryStatus.READ),
          library.countByUserIdAndStatus(userId, LibraryStatus.DROPPED),pages,avg);
    }
}
