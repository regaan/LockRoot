using System.Windows;
using Lockroot.Windows.Models;
using Lockroot.Windows.Services;

namespace Lockroot.Windows.Dialogs;

public partial class EntryEditorWindow : Window
{
    private readonly PasswordGenerator _generator = new();
    private readonly VaultEntry _entry;

    public EntryEditorWindow(VaultEntry? entry = null)
    {
        InitializeComponent();
        _entry = entry?.Clone() ?? new VaultEntry();

        HeaderText.Text = entry is null ? "Add Entry" : "Edit Entry";
        TitleBox.Text = _entry.Title;
        WebsiteBox.Text = _entry.Website;
        UsernameBox.Text = _entry.Username;
        PasswordBox.Text = _entry.Password;
        NotesBox.Text = _entry.Notes;
        TagsBox.Text = string.Join(", ", _entry.Tags);
        FavoriteBox.IsChecked = _entry.Favorite;
        TitleBox.Focus();
    }

    public VaultEntry Entry => _entry;

    private void GeneratePasswordClick(object sender, RoutedEventArgs e)
    {
        PasswordBox.Text = _generator.Generate();
    }

    private void SaveClick(object sender, RoutedEventArgs e)
    {
        ErrorText.Text = "";

        if (string.IsNullOrWhiteSpace(TitleBox.Text))
        {
            ErrorText.Text = "Title is required.";
            return;
        }

        _entry.Title = TitleBox.Text.Trim();
        _entry.Website = WebsiteBox.Text.Trim();
        _entry.Username = UsernameBox.Text.Trim();
        _entry.Password = PasswordBox.Text;
        _entry.Notes = NotesBox.Text.Trim();
        _entry.Tags = TagsBox.Text.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .ToList();
        _entry.Favorite = FavoriteBox.IsChecked == true;

        DialogResult = true;
    }

    private void CancelClick(object sender, RoutedEventArgs e)
    {
        DialogResult = false;
    }

    protected override void OnSourceInitialized(EventArgs e)
    {
        base.OnSourceInitialized(e);
        WindowCaptureProtection.Apply(this);
    }
}
