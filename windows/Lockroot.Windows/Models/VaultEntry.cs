namespace Lockroot.Windows.Models;

public sealed class VaultEntry
{
    public string Id { get; set; } = Guid.NewGuid().ToString("N");
    public string Title { get; set; } = "";
    public string Website { get; set; } = "";
    public string Username { get; set; } = "";
    public string Password { get; set; } = "";
    public string Notes { get; set; } = "";
    public List<string> Tags { get; set; } = [];
    public bool Favorite { get; set; }
    public DateTimeOffset CreatedAt { get; set; } = DateTimeOffset.UtcNow;
    public DateTimeOffset UpdatedAt { get; set; } = DateTimeOffset.UtcNow;

    public string TagsDisplay => string.Join(", ", Tags);

    public bool Matches(string query)
    {
        if (string.IsNullOrWhiteSpace(query))
        {
            return true;
        }

        return Contains(Title, query)
            || Contains(Website, query)
            || Contains(Username, query)
            || Contains(Notes, query)
            || Tags.Any(tag => Contains(tag, query));
    }

    public VaultEntry Clone() =>
        new()
        {
            Id = Id,
            Title = Title,
            Website = Website,
            Username = Username,
            Password = Password,
            Notes = Notes,
            Tags = [.. Tags],
            Favorite = Favorite,
            CreatedAt = CreatedAt,
            UpdatedAt = UpdatedAt
        };

    private static bool Contains(string value, string query) =>
        value.Contains(query, StringComparison.OrdinalIgnoreCase);
}
