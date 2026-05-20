using System.Collections.ObjectModel;
using System.IO;
using System.Security.Cryptography;
using System.Windows;
using System.Windows.Input;
using System.Windows.Threading;
using Lockroot.Windows.Dialogs;
using Lockroot.Windows.Models;
using Lockroot.Windows.Services;
using Lockroot.Windows.Vault;
using Microsoft.Win32;

namespace Lockroot.Windows;

public partial class MainWindow : Window
{
    private readonly VaultRepository _repository = new();
    private readonly PasswordGenerator _generator = new();
    private readonly AppSettingsStore _settings = new();
    private readonly ObservableCollection<VaultEntry> _visibleEntries = [];
    private readonly DispatcherTimer _clipboardTimer;
    private string _currentFilter = "all";
    private string? _lastClipboardSecret;

    public MainWindow()
    {
        InitializeComponent();

        EntriesList.ItemsSource = _visibleEntries;
        VaultPathText.Text = $"Vault file:\n{_repository.VaultPath}";

        _clipboardTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(20) };
        _clipboardTimer.Tick += ClearClipboardTimerTick;

        ShowInitialScreen();
    }

    private void ShowInitialScreen()
    {
        if (!_settings.TermsAccepted)
        {
            ShowTerms();
            return;
        }

        if (_repository.HasVault)
        {
            ShowUnlock();
        }
        else
        {
            ShowSetup();
        }
    }

    private void ShowTerms()
    {
        TermsScreen.Visibility = Visibility.Visible;
        SetupScreen.Visibility = Visibility.Collapsed;
        UnlockScreen.Visibility = Visibility.Collapsed;
        VaultShell.Visibility = Visibility.Collapsed;
    }

    private void ShowSetup()
    {
        TermsScreen.Visibility = Visibility.Collapsed;
        SetupScreen.Visibility = Visibility.Visible;
        UnlockScreen.Visibility = Visibility.Collapsed;
        VaultShell.Visibility = Visibility.Collapsed;
        SetupPasswordBox.Focus();
    }

    private void ShowUnlock()
    {
        TermsScreen.Visibility = Visibility.Collapsed;
        SetupScreen.Visibility = Visibility.Collapsed;
        UnlockScreen.Visibility = Visibility.Visible;
        VaultShell.Visibility = Visibility.Collapsed;
        UnlockPasswordBox.Password = "";
        UnlockPasswordBox.Focus();
    }

    private void ShowVault()
    {
        TermsScreen.Visibility = Visibility.Collapsed;
        SetupScreen.Visibility = Visibility.Collapsed;
        UnlockScreen.Visibility = Visibility.Collapsed;
        VaultShell.Visibility = Visibility.Visible;
        SearchBox.Text = "";
        _currentFilter = "all";
        RefreshEntries();
        SearchBox.Focus();
    }

    private void AcceptTermsClick(object sender, RoutedEventArgs e)
    {
        TermsErrorText.Text = "";

        if (TermsCheckBox.IsChecked != true)
        {
            TermsErrorText.Text = "You need to accept the Terms and Conditions before creating a vault.";
            return;
        }

        _settings.AcceptTerms();
        ShowInitialScreen();
    }

    private void CreateVaultClick(object sender, RoutedEventArgs e)
    {
        SetupErrorText.Text = "";

        if (!_settings.TermsAccepted)
        {
            ShowTerms();
            return;
        }

        if (SetupPasswordBox.Password.Length < 8)
        {
            SetupErrorText.Text = "Use at least 8 characters.";
            return;
        }

        if (SetupPasswordBox.Password != SetupConfirmPasswordBox.Password)
        {
            SetupErrorText.Text = "Passwords do not match.";
            return;
        }

        try
        {
            _repository.Create(SetupPasswordBox.Password);
            SetupPasswordBox.Password = "";
            SetupConfirmPasswordBox.Password = "";
            ShowVault();
        }
        catch (Exception ex)
        {
            SetupErrorText.Text = $"Could not create vault: {ex.Message}";
        }
    }

    private void UnlockVaultClick(object sender, RoutedEventArgs e)
    {
        UnlockErrorText.Text = "";

        try
        {
            _repository.Unlock(UnlockPasswordBox.Password);
            UnlockPasswordBox.Password = "";
            ShowVault();
        }
        catch (CryptographicException)
        {
            UnlockErrorText.Text = "Wrong password or corrupted vault.";
        }
        catch (Exception ex)
        {
            UnlockErrorText.Text = $"Could not unlock vault: {ex.Message}";
        }
    }

    private void AddEntryClick(object sender, RoutedEventArgs e)
    {
        var dialog = new EntryEditorWindow { Owner = this };
        if (dialog.ShowDialog() == true)
        {
            _repository.Upsert(dialog.Entry);
            RefreshEntries();
        }
    }

    private void EditEntryClick(object sender, RoutedEventArgs e)
    {
        var entry = EntryFromSender(sender);
        if (entry is null)
        {
            return;
        }

        var dialog = new EntryEditorWindow(entry) { Owner = this };
        if (dialog.ShowDialog() == true)
        {
            _repository.Upsert(dialog.Entry);
            RefreshEntries();
        }
    }

    private void DeleteEntryClick(object sender, RoutedEventArgs e)
    {
        var entry = EntryFromSender(sender);
        if (entry is null)
        {
            return;
        }

        var result = MessageBox.Show(
            $"Delete \"{entry.Title}\" permanently?",
            "Lockroot",
            MessageBoxButton.YesNo,
            MessageBoxImage.Warning);

        if (result != MessageBoxResult.Yes)
        {
            return;
        }

        _repository.Delete(entry.Id);
        RefreshEntries();
    }

    private void CopyPasswordClick(object sender, RoutedEventArgs e)
    {
        var entry = EntryFromSender(sender);
        if (entry is null || string.IsNullOrEmpty(entry.Password))
        {
            return;
        }

        Clipboard.SetText(entry.Password);
        _lastClipboardSecret = entry.Password;
        _clipboardTimer.Stop();
        _clipboardTimer.Start();
    }

    private void ClearClipboardTimerTick(object? sender, EventArgs e)
    {
        _clipboardTimer.Stop();

        if (_lastClipboardSecret is null)
        {
            return;
        }

        try
        {
            if (Clipboard.ContainsText() && Clipboard.GetText() == _lastClipboardSecret)
            {
                Clipboard.Clear();
            }
        }
        catch
        {
            // Clipboard can be temporarily locked by another process.
        }
        finally
        {
            _lastClipboardSecret = null;
        }
    }

    private void OpenGeneratorClick(object sender, RoutedEventArgs e)
    {
        var generated = _generator.Generate();
        Clipboard.SetText(generated);
        _lastClipboardSecret = generated;
        _clipboardTimer.Stop();
        _clipboardTimer.Start();

        MessageBox.Show(
            "Generated password copied to clipboard. It will clear automatically in 20 seconds.",
            "Lockroot",
            MessageBoxButton.OK,
            MessageBoxImage.Information);
    }

    private void ExportVaultClick(object sender, RoutedEventArgs e)
    {
        var prompt = new PasswordPromptWindow(
            "Export vault",
            "Create a separate export password. Import requires this exact password.",
            requiresConfirmation: true)
        {
            Owner = this
        };

        if (prompt.ShowDialog() != true)
        {
            return;
        }

        var saveDialog = new SaveFileDialog
        {
            Title = "Export encrypted Lockroot vault",
            FileName = $"lockroot-export-{DateTimeOffset.Now:yyyyMMdd-HHmm}.lpexport",
            Filter = "Lockroot export (*.lpexport)|*.lpexport|All files (*.*)|*.*"
        };

        if (saveDialog.ShowDialog(this) != true)
        {
            return;
        }

        try
        {
            File.WriteAllBytes(saveDialog.FileName, _repository.Export(prompt.Password));
            MessageBox.Show("Encrypted export saved.", "Lockroot", MessageBoxButton.OK, MessageBoxImage.Information);
        }
        catch (Exception ex)
        {
            MessageBox.Show($"Export failed: {ex.Message}", "Lockroot", MessageBoxButton.OK, MessageBoxImage.Error);
        }
    }

    private void ImportVaultClick(object sender, RoutedEventArgs e)
    {
        var openDialog = new OpenFileDialog
        {
            Title = "Import encrypted Lockroot vault",
            Filter = "Lockroot export (*.lpexport)|*.lpexport|All files (*.*)|*.*"
        };

        if (openDialog.ShowDialog(this) != true)
        {
            return;
        }

        var prompt = new PasswordPromptWindow(
            "Import vault",
            "Enter the export password used to encrypt this file.",
            requiresConfirmation: false)
        {
            Owner = this
        };

        if (prompt.ShowDialog() != true)
        {
            return;
        }

        try
        {
            var imported = _repository.DecryptExport(File.ReadAllBytes(openDialog.FileName), prompt.Password);
            var choice = MessageBox.Show(
                $"Import contains {imported.Entries.Count} entries.\n\nYes = merge into current vault\nNo = replace current vault\nCancel = stop",
                "Lockroot Import",
                MessageBoxButton.YesNoCancel,
                MessageBoxImage.Question);

            if (choice == MessageBoxResult.Cancel)
            {
                return;
            }

            if (choice == MessageBoxResult.Yes)
            {
                var added = _repository.Merge(imported);
                MessageBox.Show($"Merged {added} entries.", "Lockroot", MessageBoxButton.OK, MessageBoxImage.Information);
            }
            else
            {
                _repository.Replace(imported);
                MessageBox.Show("Vault replaced with imported entries.", "Lockroot", MessageBoxButton.OK, MessageBoxImage.Information);
            }

            RefreshEntries();
        }
        catch (CryptographicException)
        {
            MessageBox.Show("Wrong export password or tampered export file.", "Lockroot", MessageBoxButton.OK, MessageBoxImage.Error);
        }
        catch (Exception ex)
        {
            MessageBox.Show($"Import failed: {ex.Message}", "Lockroot", MessageBoxButton.OK, MessageBoxImage.Error);
        }
    }

    private void LockVaultClick(object sender, RoutedEventArgs e)
    {
        _repository.Lock();
        _visibleEntries.Clear();
        ShowUnlock();
    }

    private void ShowAllEntriesClick(object sender, RoutedEventArgs e)
    {
        _currentFilter = "all";
        RefreshEntries();
    }

    private void ShowFavoritesClick(object sender, RoutedEventArgs e)
    {
        _currentFilter = "favorites";
        RefreshEntries();
    }

    private void FocusSearchClick(object sender, RoutedEventArgs e) => SearchBox.Focus();

    private void SearchTextChanged(object sender, System.Windows.Controls.TextChangedEventArgs e)
    {
        if (_repository.IsUnlocked)
        {
            RefreshEntries();
        }
    }

    private void OnPreviewKeyDown(object sender, KeyEventArgs e)
    {
        if (Keyboard.Modifiers == ModifierKeys.Control && e.Key == Key.K && VaultShell.Visibility == Visibility.Visible)
        {
            SearchBox.Focus();
            SearchBox.SelectAll();
            e.Handled = true;
        }

        if (e.Key == Key.Enter && UnlockScreen.Visibility == Visibility.Visible)
        {
            UnlockVaultClick(sender, e);
            e.Handled = true;
        }
    }

    private void RefreshEntries()
    {
        if (!_repository.IsUnlocked)
        {
            return;
        }

        var query = SearchBox.Text.Trim();
        var entries = _repository.CurrentVault.Entries
            .Where(entry => _currentFilter != "favorites" || entry.Favorite)
            .Where(entry => entry.Matches(query))
            .OrderBy(entry => entry.Title, StringComparer.OrdinalIgnoreCase)
            .ToList();

        _visibleEntries.Clear();
        foreach (var entry in entries)
        {
            _visibleEntries.Add(entry);
        }

        EntryCountText.Text = _repository.CurrentVault.Entries.Count.ToString();
        FavoriteCountText.Text = _repository.CurrentVault.Entries.Count(entry => entry.Favorite).ToString();
        EmptyState.Visibility = _visibleEntries.Count == 0 ? Visibility.Visible : Visibility.Collapsed;
    }

    private VaultEntry? EntryFromSender(object sender)
    {
        if (sender is not FrameworkElement { Tag: string id })
        {
            return null;
        }

        return _repository.CurrentVault.Entries.FirstOrDefault(entry => entry.Id == id);
    }

    protected override void OnClosed(EventArgs e)
    {
        _clipboardTimer.Stop();
        _repository.Dispose();
        base.OnClosed(e);
    }
}
