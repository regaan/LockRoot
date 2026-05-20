using System.IO;
using System.Text.Json;

namespace Lockroot.Windows.Services;

public sealed class AppSettingsStore
{
    private static readonly JsonSerializerOptions JsonOptions = new() { WriteIndented = true };

    private readonly string _settingsPath = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
        "Lockroot",
        "settings.json");

    public bool TermsAccepted
    {
        get
        {
            if (!File.Exists(_settingsPath))
            {
                return false;
            }

            try
            {
                var settings = JsonSerializer.Deserialize<AppSettings>(File.ReadAllText(_settingsPath), JsonOptions);
                return settings?.TermsAccepted == true;
            }
            catch
            {
                return false;
            }
        }
    }

    public void AcceptTerms()
    {
        var directory = Path.GetDirectoryName(_settingsPath)
                        ?? throw new InvalidOperationException("Invalid settings path.");
        Directory.CreateDirectory(directory);

        var settings = new AppSettings
        {
            TermsAccepted = true,
            TermsAcceptedAt = DateTimeOffset.UtcNow
        };

        File.WriteAllText(_settingsPath, JsonSerializer.Serialize(settings, JsonOptions));
    }

    private sealed class AppSettings
    {
        public bool TermsAccepted { get; set; }
        public DateTimeOffset? TermsAcceptedAt { get; set; }
    }
}
