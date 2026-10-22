using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Design;

namespace WorkTrack.Api;

public sealed class DesignTimeDb : IDesignTimeDbContextFactory<AppDb>
{
    public AppDb CreateDbContext(string[] args)
    {
        var options = new DbContextOptionsBuilder<AppDb>().UseSqlite("Data Source=worktrack-v1.db").Options;
        return new AppDb(options);
    }
}
