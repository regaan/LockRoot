using System.Globalization;
using System.IO;
using System.Security.Cryptography;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Controls.ApplicationLifetimes;
using Avalonia.Controls.Primitives;
using Avalonia.Controls.Shapes;
using Avalonia.Input;
using Avalonia.Interactivity;
using Avalonia.Layout;
using Avalonia.Media;
using Avalonia.Media.Imaging;
using Avalonia.Platform;
using Avalonia.Platform.Storage;
using Avalonia.Threading;
using Lockroot.Linux.Models;
using Lockroot.Linux.Services;
using Lockroot.Linux.Vault;

namespace Lockroot.Linux;

public sealed class MainWindow : Window
{
    private const string AppName = "Lockroot";
    private const string AssetBase = "avares://Lockroot/Assets/";

    private static readonly IBrush BackgroundBrush = Solid("#F7F8F4");
    private static readonly IBrush SurfaceBrush = Solid("#FFFFFF");
    private static readonly IBrush MutedSurfaceBrush = Solid("#EEF5F0");
    private static readonly IBrush TextBrush = Solid("#151B18");
    private static readonly IBrush MutedTextBrush = Solid("#66736E");
    private static readonly IBrush AppBorderBrush = Solid("#DCE6DF");
    private static readonly IBrush AccentBrush = Solid("#0E6B49");
    private static readonly IBrush AccentSoftBrush = Solid("#E3F1EA");
    private static readonly IBrush DangerBrush = Solid("#B42318");

    private readonly VaultRepository _vault = new();
    private readonly PasswordGenerator _passwordGenerator = new();
    private readonly DispatcherTimer _inactivityTimer = new();
    private readonly Grid _root = new();
    private readonly List<Button> _navButtons = [];

    private Border? _sidebar;
    private StackPanel? _main;
    private Border? _overlay;
    private string _searchQuery = "";
    private int _failedUnlocks;
    private DateTimeOffset _blockedUntil;

    public MainWindow()
    {
        Title = AppName;
        Width = 1280;
        Height = 820;
        MinWidth = 860;
        MinHeight = 620;
        Background = BackgroundBrush;
        Content = _root;
        this.Icon = LoadWindowIcon();

        _inactivityTimer.Interval = TimeSpan.FromSeconds(60);
        _inactivityTimer.Tick += (_, _) => LockForInactivity();

        AddHandler(PointerPressedEvent, (_, _) => ResetInactivityTimer(), RoutingStrategies.Tunnel);
        AddHandler(KeyDownEvent, (_, _) => ResetInactivityTimer(), RoutingStrategies.Tunnel);
        PropertyChanged += (_, args) =>
        {
            if (args.Property == WindowStateProperty && WindowState == WindowState.Minimized)
            {
                LockForInactivity();
            }
        };
        Closing += (_, _) => _vault.Lock();
        SizeChanged += (_, _) => UpdateResponsiveSidebar();

        ShowInitialScreen();
    }

    private void ShowInitialScreen()
    {
        _inactivityTimer.Stop();
        if (_vault.HasVault)
        {
            ShowUnlock();
        }
        else
        {
            ShowSetup();
        }
    }

    private void ShowSetup()
    {
        var password = new TextBox { Watermark = "Master password", PasswordChar = '*' };
        var confirm = new TextBox { Watermark = "Confirm password", PasswordChar = '*' };
        var terms = new CheckBox { Content = "I agree to the Terms and Conditions", Foreground = AccentBrush };
        var strengthLabel = BodyText("Enter password", MutedTextBrush);
        var strengthBars = StrengthBars(0, "#DDE4DF");

        password.PropertyChanged += (_, args) =>
        {
            if (args.Property == TextBox.TextProperty)
            {
                var strength = PasswordRules.Evaluate(password.Text ?? "");
                strengthLabel.Text = strength.Label;
                strengthLabel.Foreground = Solid(strength.Color);
                RenderStrengthBars(strengthBars, strength.Bars, strength.Color);
            }
        };

        SetSingleScreen(new[]
        {
            CenteredLogo(140),
            Heading("Set up your vault", 42, TextAlignment.Center),
            BodyText("Create a master password to encrypt and protect your local vault.", MutedTextBrush, 18, TextAlignment.Center),
            Card(new StackPanel
            {
                Spacing = 14,
                Children =
                {
                    FieldLabel("MASTER PASSWORD"),
                    StyledPassword(password),
                    strengthBars,
                    strengthLabel,
                    FieldLabel("CONFIRM PASSWORD"),
                    StyledPassword(confirm),
                    terms
                }
            }, maxWidth: 560),
            InfoPanel("Important", "Your master password is the only way to access your vault. Lockroot cannot recover it."),
            PrimaryButton("Create Vault", "arrow", (_, _) =>
            {
                var pass = password.Text ?? "";
                var confirmPass = confirm.Text ?? "";

                if (!terms.IsChecked.GetValueOrDefault())
                {
                    ShowToast("Terms required", "You must agree to the terms before creating the vault.");
                    return;
                }

                if (pass.Length < PasswordRules.MinimumMasterPasswordLength)
                {
                    ShowToast("Weak master password", "Use at least 12 characters.");
                    return;
                }

                if (pass != confirmPass)
                {
                    ShowToast("Password mismatch", "Master password and confirmation do not match.");
                    return;
                }

                try
                {
                    _vault.Create(pass);
                    password.Text = "";
                    confirm.Text = "";
                    ShowShell("Home");
                }
                catch (Exception ex)
                {
                    ShowToast("Vault creation failed", ex.Message);
                }
            }, maxWidth: 560)
        });
    }

    private void ShowUnlock()
    {
        var password = new TextBox { Watermark = "Master password", PasswordChar = '*' };
        var status = BodyText("Enter your master password to continue.", MutedTextBrush, 16, TextAlignment.Center);

        void Unlock()
        {
            if (DateTimeOffset.UtcNow < _blockedUntil)
            {
                var seconds = Math.Ceiling((_blockedUntil - DateTimeOffset.UtcNow).TotalSeconds);
                status.Text = $"Too many attempts. Try again in {seconds:0}s.";
                status.Foreground = DangerBrush;
                return;
            }

            try
            {
                _vault.Unlock(password.Text ?? "");
                _failedUnlocks = 0;
                password.Text = "";
                ShowShell("Home");
            }
            catch
            {
                _failedUnlocks++;
                if (_failedUnlocks >= 3)
                {
                    var delay = Math.Min(30, Math.Pow(2, _failedUnlocks - 2));
                    _blockedUntil = DateTimeOffset.UtcNow.AddSeconds(delay);
                }

                status.Text = "Wrong password or corrupted vault.";
                status.Foreground = DangerBrush;
            }
        }

        password.KeyDown += (_, args) =>
        {
            if (args.Key == Key.Enter)
            {
                Unlock();
            }
        };

        SetSingleScreen(new[]
        {
            CenteredLogo(140),
            Heading("Unlock vault", 42, TextAlignment.Center),
            status,
            Card(new StackPanel
            {
                Spacing = 14,
                Children =
                {
                    FieldLabel("MASTER PASSWORD"),
                    StyledPassword(password),
                    PrimaryButton("Unlock", "arrow", (_, _) => Unlock())
                }
            }, maxWidth: 520),
            InfoPanel("Locked after inactivity", "Lockroot locks the vault when the app is minimized or left idle.")
        });
    }

    private void ShowShell(string active)
    {
        _root.Children.Clear();
        _overlay = null;
        _navButtons.Clear();

        var shell = new Grid
        {
            ColumnDefinitions = new ColumnDefinitions("280,*"),
            Background = BackgroundBrush
        };

        _sidebar = BuildSidebar(active);
        Grid.SetColumn(_sidebar, 0);
        shell.Children.Add(_sidebar);

        var mainScroll = new ScrollViewer
        {
            HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled,
            VerticalScrollBarVisibility = ScrollBarVisibility.Auto
        };
        _main = new StackPanel
        {
            Spacing = 24,
            Margin = new Thickness(38, 28, 38, 36)
        };
        mainScroll.Content = _main;
        Grid.SetColumn(mainScroll, 1);
        shell.Children.Add(mainScroll);

        _root.Children.Add(shell);
        UpdateResponsiveSidebar();
        ResetInactivityTimer();
        ShowDashboard();
    }

    private Border BuildSidebar(string active)
    {
        var stack = new StackPanel { Spacing = 14, Margin = new Thickness(24) };
        stack.Children.Add(new StackPanel
        {
            Orientation = Orientation.Horizontal,
            Spacing = 12,
            Margin = new Thickness(0, 8, 0, 26),
            Children =
            {
                new Image { Source = LoadBitmap("lockroot-icon.png"), Width = 48, Height = 48 },
                new StackPanel
                {
                    VerticalAlignment = VerticalAlignment.Center,
                    Children =
                    {
                        Heading("Lockroot", 22),
                        BodyText("Password Manager", MutedTextBrush)
                    }
                }
            }
        });

        stack.Children.Add(NavButton("Home", "home", ShowDashboard, active == "Home"));
        stack.Children.Add(NavButton("All Entries", "list", ShowAllEntries, active == "All Entries"));
        stack.Children.Add(SectionLabel("TOOLS"));
        stack.Children.Add(NavButton("Password Generator", "spark", ShowGenerator, active == "Password Generator"));
        stack.Children.Add(NavButton("Import", "download", ShowImportPicker, active == "Import"));
        stack.Children.Add(NavButton("Export", "upload", ShowExport, active == "Export"));
        stack.Children.Add(SectionLabel("SECURITY"));
        stack.Children.Add(NavButton("Master Password", "lock", ShowChangeMasterPassword, active == "Master Password"));
        stack.Children.Add(NavButton("Settings", "settings", ShowSettings, active == "Settings"));

        stack.Children.Add(new Border { Height = 20 });
        stack.Children.Add(Card(new StackPanel
        {
            Orientation = Orientation.Horizontal,
            Spacing = 12,
            Children =
            {
                IconBubble("shield", 46),
                new StackPanel
                {
                    Children =
                    {
                        Heading("Your data is safe", 14),
                        BodyText("All passwords are encrypted locally.", MutedTextBrush, 13)
                    }
                }
            }
        }, padding: 14, radius: 14));

        return new Border
        {
            Background = SurfaceBrush,
            BorderBrush = AppBorderBrush,
            BorderThickness = new Thickness(0, 0, 1, 0),
            Child = stack
        };
    }

    private void ShowDashboard()
    {
        SetActiveNav("Home");
        var vault = _vault.CurrentVault;
        var visible = FilteredEntries();
        _main!.Children.Clear();
        _main.Children.Add(BuildTopBar());
        _main.Children.Add(new WrapPanel
        {
            ItemWidth = 260,
            ItemHeight = 132,
            Children =
            {
                StatCard("Total Entries", vault.Entries.Count.ToString(CultureInfo.InvariantCulture), "All your saved entries", "vault"),
                StatCard("Favorites", vault.Entries.Count(entry => entry.Favorite).ToString(CultureInfo.InvariantCulture), "Entries marked favorite", "star")
            }
        });

        _main.Children.Add(new WrapPanel
        {
            ItemWidth = 260,
            ItemHeight = 112,
            Children =
            {
                ActionCard("Search", "Find your entries", "search", () => FocusSearch()),
                ActionCard("Add Entry", "Create a new entry", "plus", () => ShowEntryEditor(null)),
                ActionCard("Import", "Import from file", "download", ShowImportPicker),
                ActionCard("Export", "Export to file", "upload", ShowExport),
                ActionCard("Password Generator", "Generate strong passwords", "shield", ShowGenerator),
                ActionCard("Settings", "Vault and legal options", "settings", ShowSettings)
            }
        });

        _main.Children.Add(visible.Count == 0 ? EmptyState() : EntriesPanel(visible));
    }

    private Control BuildTopBar()
    {
        var search = new TextBox
        {
            Watermark = "Search entries...",
            Text = _searchQuery,
            MinWidth = 420,
            Height = 48,
            Padding = new Thickness(42, 0, 16, 0),
            VerticalContentAlignment = VerticalAlignment.Center,
            BorderBrush = AppBorderBrush,
            CornerRadius = new CornerRadius(14),
            Background = SurfaceBrush
        };

        search.TextChanged += (_, _) =>
        {
            _searchQuery = search.Text ?? "";
            ShowDashboard();
        };

        var searchWrap = new Grid { Width = 520 };
        searchWrap.Children.Add(search);
        searchWrap.Children.Add(new Border
        {
            Margin = new Thickness(14, 0, 0, 0),
            Width = 20,
            Height = 20,
            HorizontalAlignment = HorizontalAlignment.Left,
            VerticalAlignment = VerticalAlignment.Center,
            Child = SvgIcon("search", 20)
        });

        return new DockPanel
        {
            LastChildFill = false,
            Children =
            {
                searchWrap,
                DockRight(PrimaryButton("Add Entry", "plus", (_, _) => ShowEntryEditor(null), width: 150)),
                DockRight(SecondaryButton("Lock", "lock", (_, _) => { _vault.Lock(); ShowUnlock(); }, width: 110))
            }
        };
    }

    private void ShowAllEntries()
    {
        SetActiveNav("All Entries");
        _main!.Children.Clear();
        _main.Children.Add(BuildTopBar());
        _main.Children.Add(Heading("All Entries", 34));
        var entries = FilteredEntries();
        _main.Children.Add(entries.Count == 0 ? EmptyState() : EntriesPanel(entries));
    }

    private void ShowEntryEditor(VaultEntry? existing)
    {
        SetActiveNav("");
        var entry = existing?.Clone() ?? new VaultEntry();
        var title = new TextBox { Text = entry.Title, Watermark = "e.g. My Google Account" };
        var website = new TextBox { Text = entry.Website, Watermark = "e.g. google.com" };
        var username = new TextBox { Text = entry.Username, Watermark = "Enter username" };
        var password = new TextBox { Text = entry.Password, Watermark = "Enter password" };
        var notes = new TextBox { Text = entry.Notes, Watermark = "Add a note", AcceptsReturn = true, MinHeight = 120 };
        var tags = new TextBox { Text = entry.TagsDisplay, Watermark = "Tags, comma separated" };
        var favorite = new CheckBox { Content = "Mark as favorite", IsChecked = entry.Favorite };
        var strengthLabel = BodyText(PasswordRules.Evaluate(password.Text ?? "").Label, MutedTextBrush);
        var bars = StrengthBars(PasswordRules.Evaluate(password.Text ?? "").Bars, PasswordRules.Evaluate(password.Text ?? "").Color);

        password.TextChanged += (_, _) =>
        {
            var strength = PasswordRules.Evaluate(password.Text ?? "");
            strengthLabel.Text = strength.Label;
            strengthLabel.Foreground = Solid(strength.Color);
            RenderStrengthBars(bars, strength.Bars, strength.Color);
        };

        _main!.Children.Clear();
        _main.Children.Add(new DockPanel
        {
            LastChildFill = false,
            Children =
            {
                Heading(existing is null ? "Add Entry" : "Edit Entry", 38),
                DockRight(SecondaryButton("Cancel", "close", (_, _) => ShowDashboard(), width: 120)),
                DockRight(PrimaryButton("Save", "check", (_, _) =>
                {
                    if (string.IsNullOrWhiteSpace(title.Text))
                    {
                        ShowToast("Title required", "Add a title before saving this entry.");
                        return;
                    }

                    entry.Title = title.Text.Trim();
                    entry.Website = website.Text?.Trim() ?? "";
                    entry.Username = username.Text?.Trim() ?? "";
                    entry.Password = password.Text ?? "";
                    entry.Notes = notes.Text?.Trim() ?? "";
                    entry.Tags = ParseTags(tags.Text);
                    entry.Favorite = favorite.IsChecked.GetValueOrDefault();
                    _vault.Upsert(entry);
                    ShowDashboard();
                }, width: 120))
            }
        });

        _main.Children.Add(Card(new StackPanel
        {
            Spacing = 16,
            Children =
            {
                Field("TITLE", title),
                Field("WEBSITE", website),
                Field("USERNAME", username),
                new Grid
                {
                    ColumnDefinitions = new ColumnDefinitions("*,150"),
                    ColumnSpacing = 12,
                    Children =
                    {
                        Field("PASSWORD", password),
                        GridAt(SecondaryButton("Generate", "spark", (_, _) =>
                        {
                            password.Text = _passwordGenerator.Generate();
                        }), 1)
                    }
                },
                bars,
                strengthLabel,
                Field("NOTES", notes),
                Field("TAGS", tags),
                favorite
            }
        }, maxWidth: 760));
    }

    private void ShowGenerator()
    {
        SetActiveNav("Password Generator");
        var length = 24;
        var output = new TextBox
        {
            IsReadOnly = true,
            Text = "Tap Generate",
            FontFamily = FontFamily.Parse("monospace"),
            FontSize = 18
        };
        var lengthText = Heading($"{length} characters", 20);
        var lower = new CheckBox { Content = "Lowercase", IsChecked = true };
        var upper = new CheckBox { Content = "Uppercase", IsChecked = true };
        var numbers = new CheckBox { Content = "Numbers", IsChecked = true };
        var symbols = new CheckBox { Content = "Symbols", IsChecked = true };

        void Generate()
        {
            try
            {
                output.Text = _passwordGenerator.Generate(
                    length,
                    lower.IsChecked.GetValueOrDefault(),
                    upper.IsChecked.GetValueOrDefault(),
                    numbers.IsChecked.GetValueOrDefault(),
                    symbols.IsChecked.GetValueOrDefault());
            }
            catch (Exception ex)
            {
                ShowToast("Generator failed", ex.Message);
            }
        }

        _main!.Children.Clear();
        _main.Children.Add(Heading("Password Generator", 38));
        _main.Children.Add(Card(new StackPanel
        {
            Spacing = 22,
            Children =
            {
                FieldLabel("LENGTH"),
                new DockPanel
                {
                    LastChildFill = true,
                    Children =
                    {
                        DockRight(new StackPanel
                        {
                            Orientation = Orientation.Horizontal,
                            Spacing = 10,
                            Children =
                            {
                                IconButton("minus", (_, _) =>
                                {
                                    length = Math.Max(PasswordGenerator.MinLength, length - 1);
                                    lengthText.Text = $"{length} characters";
                                }),
                                IconButton("plus", (_, _) =>
                                {
                                    length = Math.Min(PasswordGenerator.MaxLength, length + 1);
                                    lengthText.Text = $"{length} characters";
                                })
                            }
                        }),
                        lengthText
                    }
                },
                FieldLabel("CHARACTER GROUPS"),
                ToggleRow(lower),
                ToggleRow(upper),
                ToggleRow(numbers),
                ToggleRow(symbols),
                FieldLabel("GENERATED"),
                StyledTextBox(output),
                new StackPanel
                {
                    Orientation = Orientation.Horizontal,
                    Spacing = 12,
                    Children =
                    {
                        PrimaryButton("Generate", "spark", (_, _) => Generate(), width: 160),
                        SecondaryButton("Copy", "copy", async (_, _) => await CopySecret(output.Text ?? ""), width: 120)
                    }
                }
            }
        }, maxWidth: 640));
    }

    private void ShowSettings()
    {
        SetActiveNav("Settings");
        _main!.Children.Clear();
        _main.Children.Add(Heading("Settings", 38));
        _main.Children.Add(SettingsGroup("Vault",
            SettingsItem("Export encrypted vault", "Create a password-protected export file", "upload", ShowExport),
            SettingsItem("Import encrypted vault", "Preview, merge, or replace entries", "download", ShowImportPicker),
            SettingsItem("Change master password", "Re-key the local vault", "key", ShowChangeMasterPassword),
            SettingsItem("Clear all entries", "Remove every saved password from this vault", "trash", ConfirmClearAll, DangerBrush)));
        _main.Children.Add(SettingsGroup("Legal",
            SettingsItem("Privacy Policy", "Local-only, no telemetry, no account", "privacy", () => ShowLegal("Privacy Policy", LegalText.Privacy)),
            SettingsItem("Terms and Conditions", "No recovery and user responsibility", "document", () => ShowLegal("Terms and Conditions", LegalText.Terms)),
            SettingsItem("About Lockroot", "Creator, license, and technical details", "info", () => ShowLegal("About Lockroot", LegalText.About))));
    }

    private void ShowLegal(string title, string text)
    {
        SetActiveNav("");
        _main!.Children.Clear();
        _main.Children.Add(new DockPanel
        {
            LastChildFill = false,
            Children =
            {
                Heading(title, 38),
                DockRight(SecondaryButton("Back", "arrow-left", (_, _) => ShowSettings(), width: 110))
            }
        });
        _main.Children.Add(Card(BodyText(text, MutedTextBrush, 16), maxWidth: 820));
    }

    private void ShowChangeMasterPassword()
    {
        SetActiveNav("Master Password");
        var current = new TextBox { Watermark = "Current master password", PasswordChar = '*' };
        var next = new TextBox { Watermark = "New master password", PasswordChar = '*' };
        var confirm = new TextBox { Watermark = "Confirm new master password", PasswordChar = '*' };

        ShowOverlay("Change master password", new StackPanel
        {
            Spacing = 14,
            Children =
            {
                BodyText("This re-encrypts your local vault. The old password stops working after success.", MutedTextBrush),
                StyledPassword(current),
                StyledPassword(next),
                StyledPassword(confirm),
                new StackPanel
                {
                    Orientation = Orientation.Horizontal,
                    HorizontalAlignment = HorizontalAlignment.Right,
                    Spacing = 12,
                    Children =
                    {
                        SecondaryButton("Cancel", "close", (_, _) => HideOverlay(), width: 110),
                        PrimaryButton("Change", "check", (_, _) =>
                        {
                            if ((next.Text ?? "").Length < PasswordRules.MinimumMasterPasswordLength)
                            {
                                ShowToast("Weak password", "Use at least 12 characters.");
                                return;
                            }

                            if (next.Text != confirm.Text)
                            {
                                ShowToast("Password mismatch", "New password and confirmation do not match.");
                                return;
                            }

                            try
                            {
                                _vault.ChangeMasterPassword(current.Text ?? "", next.Text ?? "");
                                HideOverlay();
                                ShowToast("Master password changed", "Your vault has been re-encrypted.");
                            }
                            catch
                            {
                                ShowToast("Change failed", "Current password is wrong or vault authentication failed.");
                            }
                        }, width: 120)
                    }
                }
            }
        });
    }

    private void ShowExport()
    {
        SetActiveNav("Export");
        var exportPassword = new TextBox { Watermark = "Export password", PasswordChar = '*' };
        ShowOverlay("Export vault", new StackPanel
        {
            Spacing = 14,
            Children =
            {
                BodyText("Create a separate export password. Import requires this exact password.", MutedTextBrush),
                StyledPassword(exportPassword),
                new StackPanel
                {
                    Orientation = Orientation.Horizontal,
                    HorizontalAlignment = HorizontalAlignment.Right,
                    Spacing = 12,
                    Children =
                    {
                        SecondaryButton("Cancel", "close", (_, _) => HideOverlay(), width: 110),
                        PrimaryButton("Continue", "arrow", async (_, _) =>
                        {
                            if ((exportPassword.Text ?? "").Length < PasswordRules.MinimumMasterPasswordLength)
                            {
                                ShowToast("Weak export password", "Use at least 12 characters.");
                                return;
                            }

                            var file = await StorageProvider.SaveFilePickerAsync(new FilePickerSaveOptions
                            {
                                Title = "Export encrypted Lockroot vault",
                                SuggestedFileName = "lockroot-export.lpexport",
                                DefaultExtension = "lpexport",
                                FileTypeChoices =
                                [
                                    new FilePickerFileType("Lockroot export")
                                    {
                                        Patterns = ["*.lpexport"]
                                    }
                                ]
                            });
                            if (file is null)
                            {
                                return;
                            }

                            try
                            {
                                var bytes = _vault.Export(exportPassword.Text ?? "");
                                await using var stream = await file.OpenWriteAsync();
                                await stream.WriteAsync(bytes);
                                HideOverlay();
                                ShowToast("Export saved", "Encrypted export file created.");
                            }
                            catch (Exception ex)
                            {
                                ShowToast("Export failed", ex.Message);
                            }
                        }, width: 130)
                    }
                }
            }
        });
    }

    private async void ShowImportPicker()
    {
        SetActiveNav("Import");
        var files = await StorageProvider.OpenFilePickerAsync(new FilePickerOpenOptions
        {
            Title = "Import encrypted Lockroot vault",
            AllowMultiple = false,
            FileTypeFilter =
            [
                new FilePickerFileType("Lockroot export")
                {
                    Patterns = ["*.lpexport", "*.json", "*.vault"]
                }
            ]
        });
        var file = files.FirstOrDefault();
        if (file is null)
        {
            return;
        }

        var exportPassword = new TextBox { Watermark = "Export password", PasswordChar = '*' };
        ShowOverlay("Import vault", new StackPanel
        {
            Spacing = 14,
            Children =
            {
                BodyText("Wrong password or tampered files fail authentication.", MutedTextBrush),
                StyledPassword(exportPassword),
                new StackPanel
                {
                    Orientation = Orientation.Horizontal,
                    HorizontalAlignment = HorizontalAlignment.Right,
                    Spacing = 12,
                    Children =
                    {
                        SecondaryButton("Cancel", "close", (_, _) => HideOverlay(), width: 110),
                        PrimaryButton("Preview", "eye", async (_, _) =>
                        {
                            try
                            {
                                await using var input = await file.OpenReadAsync();
                                using var memory = new MemoryStream();
                                await input.CopyToAsync(memory);
                                var bytes = memory.ToArray();
                                var imported = _vault.DecryptExport(bytes, exportPassword.Text ?? "");
                                HideOverlay();
                                ShowImportPreview(imported);
                            }
                            catch
                            {
                                ShowToast("Import failed", "Wrong export password or corrupted export file.");
                            }
                        }, width: 120)
                    }
                }
            }
        });
    }

    private void ShowImportPreview(VaultDocument imported)
    {
        _main!.Children.Clear();
        _main.Children.Add(Heading("Import preview", 38));
        _main.Children.Add(BodyText($"{imported.Entries.Count} entries found. Review before merging or replacing your vault.", MutedTextBrush, 16));
        _main.Children.Add(EntriesPanel(imported.Entries.Select(entry => entry.Clone()).ToList(), readOnly: true));
        _main.Children.Add(new StackPanel
        {
            Orientation = Orientation.Horizontal,
            Spacing = 12,
            Children =
            {
                SecondaryButton("Cancel", "close", (_, _) => ShowDashboard(), width: 120),
                PrimaryButton("Merge", "plus", (_, _) =>
                {
                    var count = _vault.Merge(imported);
                    ShowToast("Import complete", $"{count} entries merged.");
                    ShowDashboard();
                }, width: 120),
                PrimaryButton("Replace", "download", (_, _) =>
                {
                    _vault.Replace(imported);
                    ShowToast("Vault replaced", "Imported entries replaced the current vault.");
                    ShowDashboard();
                }, width: 130)
            }
        });
    }

    private void ConfirmClearAll()
    {
        ShowOverlay("Clear all entries", new StackPanel
        {
            Spacing = 14,
            Children =
            {
                BodyText("This removes every saved entry from the unlocked vault. Your master password and vault file remain.", MutedTextBrush),
                new StackPanel
                {
                    Orientation = Orientation.Horizontal,
                    HorizontalAlignment = HorizontalAlignment.Right,
                    Spacing = 12,
                    Children =
                    {
                        SecondaryButton("Cancel", "close", (_, _) => HideOverlay(), width: 110),
                        PrimaryButton("Clear", "trash", (_, _) =>
                        {
                            _vault.ClearAll();
                            HideOverlay();
                            ShowDashboard();
                        }, width: 110)
                    }
                }
            }
        });
    }

    private async Task CopySecret(string text)
    {
        if (string.IsNullOrEmpty(text) || text == "Tap Generate")
        {
            return;
        }

        var clipboard = TopLevel.GetTopLevel(this)?.Clipboard;
        if (clipboard is null)
        {
            ShowToast("Clipboard unavailable", "This Linux session did not expose a clipboard.");
            return;
        }

        await clipboard.SetTextAsync(text);
        ShowToast("Copied", "Clipboard will be cleared in 20 seconds.");

        _ = Task.Run(async () =>
        {
            await Task.Delay(TimeSpan.FromSeconds(20));
            await Dispatcher.UIThread.InvokeAsync(async () =>
            {
                await clipboard.SetTextAsync("");
            });
        });
    }

    private List<VaultEntry> FilteredEntries() =>
        _vault.CurrentVault.Entries
            .Where(entry => entry.Matches(_searchQuery))
            .OrderBy(entry => entry.Title, StringComparer.OrdinalIgnoreCase)
            .ToList();

    private Control EntriesPanel(IReadOnlyList<VaultEntry> entries, bool readOnly = false)
    {
        var panel = new StackPanel { Spacing = 12 };
        foreach (var entry in entries)
        {
            panel.Children.Add(EntryCard(entry, readOnly));
        }

        return Card(panel);
    }

    private Control EntryCard(VaultEntry entry, bool readOnly)
    {
        var actions = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8 };
        if (!readOnly)
        {
            actions.Children.Add(SecondaryButton("Copy", "copy", async (_, _) => await CopySecret(entry.Password), width: 90));
            actions.Children.Add(SecondaryButton("Edit", "edit", (_, _) => ShowEntryEditor(entry), width: 82));
            actions.Children.Add(SecondaryButton("Delete", "trash", (_, _) =>
            {
                _vault.Delete(entry.Id);
                ShowDashboard();
            }, width: 94));
        }

        return new Border
        {
            Padding = new Thickness(16),
            CornerRadius = new CornerRadius(18),
            Background = Solid("#FBFCFA"),
            BorderBrush = AppBorderBrush,
            BorderThickness = new Thickness(1),
            Child = new DockPanel
            {
                LastChildFill = true,
                Children =
                {
                    DockRight(actions),
                    new StackPanel
                    {
                        Spacing = 4,
                        Children =
                        {
                            Heading(entry.Title, 18),
                            BodyText(string.IsNullOrWhiteSpace(entry.Username) ? entry.Website : entry.Username, MutedTextBrush),
                            string.IsNullOrWhiteSpace(entry.TagsDisplay) ? new Border() : BodyText(entry.TagsDisplay, AccentBrush, 13)
                        }
                    }
                }
            }
        };
    }

    private Border EmptyState() =>
        Card(new StackPanel
        {
            HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Center,
            Spacing = 16,
            MinHeight = 300,
            Children =
            {
                IconBubble("lock", 96),
                Heading("No entries yet", 26, TextAlignment.Center),
                BodyText("Add your first entry to get started.", MutedTextBrush, 16, TextAlignment.Center),
                PrimaryButton("Add Entry", "plus", (_, _) => ShowEntryEditor(null), width: 150)
            }
        });

    private Control StatCard(string title, string value, string subtitle, string icon) =>
        Card(new StackPanel
        {
            Orientation = Orientation.Horizontal,
            Spacing = 16,
            Children =
            {
                IconBubble(icon, 58),
                new StackPanel
                {
                    VerticalAlignment = VerticalAlignment.Center,
                    Children =
                    {
                        BodyText(title, TextBrush, 15),
                        Heading(value, 30),
                        BodyText(subtitle, MutedTextBrush, 13)
                    }
                }
            }
        }, margin: new Thickness(0, 0, 14, 14));

    private Control ActionCard(string title, string subtitle, string icon, Action action) =>
        new Button
        {
            Padding = new Thickness(0),
            Background = Brushes.Transparent,
            BorderBrush = Brushes.Transparent,
            Content = Card(new StackPanel
            {
                Orientation = Orientation.Horizontal,
                Spacing = 16,
                Children =
                {
                    IconBubble(icon, 56),
                    new StackPanel
                    {
                        VerticalAlignment = VerticalAlignment.Center,
                        Children =
                        {
                            Heading(title, 17),
                            BodyText(subtitle, MutedTextBrush, 14)
                        }
                    }
                }
            }, margin: new Thickness(0, 0, 14, 14), padding: 18)
        }.Also(button => button.Click += (_, _) => action());

    private Control SettingsGroup(string title, params Control[] items)
    {
        var stack = new StackPanel { Spacing = 0 };
        var itemStack = new StackPanel();
        foreach (var item in items)
        {
            itemStack.Children.Add(item);
        }

        stack.Children.Add(SectionLabel(title));
        stack.Children.Add(Card(itemStack, padding: 0, maxWidth: 820));
        return stack;
    }

    private Control SettingsItem(string title, string subtitle, string icon, Action action, IBrush? color = null) =>
        new Button
        {
            HorizontalContentAlignment = HorizontalAlignment.Stretch,
            Background = Brushes.Transparent,
            BorderBrush = Brushes.Transparent,
            Padding = new Thickness(0),
            Content = new Border
            {
                Padding = new Thickness(20, 16),
                Child = new DockPanel
                {
                    LastChildFill = true,
                    Children =
                    {
                        IconBubble(icon, 46, color),
                        new StackPanel
                        {
                            Margin = new Thickness(16, 0, 0, 0),
                            Children =
                            {
                                Heading(title, 18).Also(text => text.Foreground = color ?? TextBrush),
                                BodyText(subtitle, MutedTextBrush, 14)
                            }
                        }
                    }
                }
            }
        }.Also(button => button.Click += (_, _) => action());

    private Button NavButton(string text, string icon, Action action, bool active)
    {
        var button = new Button
        {
            HorizontalContentAlignment = HorizontalAlignment.Stretch,
            Background = active ? AccentSoftBrush : Brushes.Transparent,
            BorderBrush = Brushes.Transparent,
            CornerRadius = new CornerRadius(12),
            Padding = new Thickness(14, 12),
            Content = new StackPanel
            {
                Orientation = Orientation.Horizontal,
                Spacing = 14,
                Children =
                {
                    SvgIcon(icon, 22, active ? AccentBrush : TextBrush),
                    BodyText(text, active ? AccentBrush : TextBrush, 15)
                }
            }
        };
        button.Click += (_, _) => action();
        _navButtons.Add(button);
        return button;
    }

    private void SetActiveNav(string label)
    {
        foreach (var button in _navButtons)
        {
            button.Background = Brushes.Transparent;
        }
    }

    private void SetSingleScreen(IEnumerable<Control> controls)
    {
        _root.Children.Clear();
        var stack = new StackPanel
        {
            Spacing = 24,
            Margin = new Thickness(32, 42),
            HorizontalAlignment = HorizontalAlignment.Center
        };

        foreach (var control in controls)
        {
            stack.Children.Add(control);
        }

        var scroll = new ScrollViewer
        {
            Content = stack
        };
        _root.Children.Add(scroll);
    }

    private void ShowOverlay(string title, Control content)
    {
        HideOverlay();
        _overlay = new Border
        {
            Background = Solid("#88000000"),
            Child = new Border
            {
                MaxWidth = 520,
                Padding = new Thickness(28),
                CornerRadius = new CornerRadius(24),
                Background = SurfaceBrush,
                BorderBrush = AppBorderBrush,
                BorderThickness = new Thickness(1),
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center,
                Child = new StackPanel
                {
                    Spacing = 16,
                    Children =
                    {
                        Heading(title, 28),
                        content
                    }
                }
            }
        };
        _root.Children.Add(_overlay);
    }

    private void HideOverlay()
    {
        if (_overlay is not null)
        {
            _root.Children.Remove(_overlay);
            _overlay = null;
        }
    }

    private void ShowToast(string title, string message)
    {
        ShowOverlay(title, new StackPanel
        {
            Spacing = 18,
            Children =
            {
                BodyText(message, MutedTextBrush),
                PrimaryButton("OK", "check", (_, _) => HideOverlay(), width: 100)
            }
        });
    }

    private void LockForInactivity()
    {
        if (!_vault.IsUnlocked)
        {
            return;
        }

        _vault.Lock();
        _inactivityTimer.Stop();
        ShowUnlock();
    }

    private void ResetInactivityTimer()
    {
        if (!_vault.IsUnlocked)
        {
            return;
        }

        _inactivityTimer.Stop();
        _inactivityTimer.Start();
    }

    private void UpdateResponsiveSidebar()
    {
        if (_sidebar is null || _root.Children.FirstOrDefault() is not Grid shell)
        {
            return;
        }

        var compact = Bounds.Width < 980;
        _sidebar.IsVisible = !compact;
        shell.ColumnDefinitions[0].Width = compact ? new GridLength(0) : new GridLength(280);
    }

    private void FocusSearch()
    {
        ShowDashboard();
    }

    private static List<string> ParseTags(string? text) =>
        (text ?? "")
            .Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .ToList();

    private static Control Field(string label, TextBox textBox) =>
        new StackPanel
        {
            Spacing = 8,
            Children =
            {
                FieldLabel(label),
                StyledTextBox(textBox)
            }
        };

    private static TextBlock FieldLabel(string text) =>
        new()
        {
            Text = text,
            Foreground = MutedTextBrush,
            FontWeight = FontWeight.SemiBold,
            FontSize = 12,
            LetterSpacing = 1.2
        };

    private static TextBlock SectionLabel(string text) =>
        new()
        {
            Text = text,
            Foreground = MutedTextBrush,
            FontSize = 13,
            FontWeight = FontWeight.SemiBold,
            Margin = new Thickness(10, 16, 0, 8)
        };

    private static TextBlock Heading(string text, double size, TextAlignment alignment = TextAlignment.Left) =>
        new()
        {
            Text = text,
            Foreground = TextBrush,
            FontSize = size,
            FontWeight = FontWeight.Bold,
            TextAlignment = alignment,
            TextWrapping = TextWrapping.Wrap
        };

    private static TextBlock BodyText(string text, IBrush brush, double size = 14, TextAlignment alignment = TextAlignment.Left) =>
        new()
        {
            Text = text,
            Foreground = brush,
            FontSize = size,
            TextAlignment = alignment,
            TextWrapping = TextWrapping.Wrap,
            LineHeight = size * 1.45
        };

    private static TextBox StyledTextBox(TextBox textBox)
    {
        textBox.Height = textBox.AcceptsReturn ? double.NaN : 46;
        textBox.Padding = new Thickness(14, 0, 14, 0);
        textBox.VerticalContentAlignment = VerticalAlignment.Center;
        textBox.Background = SurfaceBrush;
        textBox.BorderBrush = AppBorderBrush;
        textBox.CornerRadius = new CornerRadius(12);
        textBox.FontSize = 15;
        return textBox;
    }

    private static TextBox StyledPassword(TextBox password)
    {
        password.Height = 46;
        password.Padding = new Thickness(14, 0, 14, 0);
        password.Background = SurfaceBrush;
        password.BorderBrush = AppBorderBrush;
        password.CornerRadius = new CornerRadius(12);
        password.FontSize = 15;
        return password;
    }

    private static Border Card(Control child, double radius = 18, double padding = 22, double maxWidth = double.PositiveInfinity, Thickness? margin = null) =>
        new()
        {
            Child = child,
            Padding = new Thickness(padding),
            CornerRadius = new CornerRadius(radius),
            Background = SurfaceBrush,
            BorderBrush = AppBorderBrush,
            BorderThickness = new Thickness(1),
            MaxWidth = maxWidth,
            Margin = margin ?? new Thickness(0)
        };

    private static Control InfoPanel(string title, string text) =>
        Card(new StackPanel
        {
            Orientation = Orientation.Horizontal,
            Spacing = 14,
            Children =
            {
                IconBubble("shield", 50),
                new StackPanel
                {
                    Width = 420,
                    Children =
                    {
                        Heading(title, 18),
                        BodyText(text, MutedTextBrush, 15)
                    }
                }
            }
        }, maxWidth: 560);

    private static Image CenteredLogo(double size) =>
        new()
        {
            Source = LoadBitmap("lockroot-icon.png"),
            Width = size,
            Height = size,
            HorizontalAlignment = HorizontalAlignment.Center
        };

    private static Button PrimaryButton(string text, string icon, EventHandler<RoutedEventArgs> handler, double width = double.NaN, double maxWidth = double.PositiveInfinity)
    {
        var button = new Button
        {
            Width = width,
            MaxWidth = maxWidth,
            MinHeight = 46,
            CornerRadius = new CornerRadius(12),
            Background = AccentBrush,
            Foreground = Brushes.White,
            BorderBrush = AccentBrush,
            Padding = new Thickness(18, 10),
            HorizontalContentAlignment = HorizontalAlignment.Center,
            Content = new StackPanel
            {
                Orientation = Orientation.Horizontal,
                Spacing = 10,
                HorizontalAlignment = HorizontalAlignment.Center,
                Children =
                {
                    SvgIcon(icon, 18, Brushes.White),
                    new TextBlock { Text = text, FontWeight = FontWeight.SemiBold, FontSize = 15 }
                }
            }
        };
        button.Click += handler;
        return button;
    }

    private static Button SecondaryButton(string text, string icon, EventHandler<RoutedEventArgs> handler, double width = double.NaN)
    {
        var button = new Button
        {
            Width = width,
            MinHeight = 42,
            CornerRadius = new CornerRadius(12),
            Background = AccentSoftBrush,
            Foreground = AccentBrush,
            BorderBrush = AppBorderBrush,
            Padding = new Thickness(14, 8),
            HorizontalContentAlignment = HorizontalAlignment.Center,
            Content = new StackPanel
            {
                Orientation = Orientation.Horizontal,
                Spacing = 8,
                HorizontalAlignment = HorizontalAlignment.Center,
                Children =
                {
                    SvgIcon(icon, 16, AccentBrush),
                    new TextBlock { Text = text, FontWeight = FontWeight.SemiBold, FontSize = 14 }
                }
            }
        };
        button.Click += handler;
        return button;
    }

    private static Button IconButton(string icon, EventHandler<RoutedEventArgs> handler)
    {
        var button = new Button
        {
            Width = 44,
            Height = 38,
            CornerRadius = new CornerRadius(12),
            Content = SvgIcon(icon, 18, AccentBrush),
            Background = AccentSoftBrush,
            BorderBrush = AppBorderBrush
        };
        button.Click += handler;
        return button;
    }

    private static Border IconBubble(string icon, double size, IBrush? color = null) =>
        new()
        {
            Width = size,
            Height = size,
            CornerRadius = new CornerRadius(size / 2),
            Background = color == DangerBrush ? Solid("#FDECEC") : AccentSoftBrush,
            Child = SvgIcon(icon, Math.Max(18, size * 0.42), color ?? AccentBrush)
        };

    private static Control ToggleRow(CheckBox checkBox) =>
        new Border
        {
            Padding = new Thickness(16, 12),
            CornerRadius = new CornerRadius(14),
            Background = Solid("#FBFCFA"),
            BorderBrush = AppBorderBrush,
            BorderThickness = new Thickness(1),
            Child = checkBox
        };

    private static StackPanel StrengthBars(int active, string color)
    {
        var bars = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8 };
        RenderStrengthBars(bars, active, color);
        return bars;
    }

    private static void RenderStrengthBars(StackPanel panel, int active, string color)
    {
        panel.Children.Clear();
        for (var index = 0; index < 4; index++)
        {
            panel.Children.Add(new Border
            {
                Width = 84,
                Height = 6,
                CornerRadius = new CornerRadius(3),
                Background = index < active ? Solid(color) : Solid("#DDE4DF")
            });
        }
    }

    private static Control DockRight(Control control)
    {
        DockPanel.SetDock(control, Dock.Right);
        return control;
    }

    private static Control GridAt(Control control, int column)
    {
        Grid.SetColumn(control, column);
        return control;
    }

    private static IBrush Solid(string color) => new SolidColorBrush(Color.Parse(color));

    private static WindowIcon? LoadWindowIcon()
    {
        try
        {
            return new WindowIcon(AssetLoader.Open(new Uri(AssetBase + "lockroot-icon.png")));
        }
        catch
        {
            return null;
        }
    }

    private static Bitmap LoadBitmap(string asset)
    {
        using var stream = AssetLoader.Open(new Uri(AssetBase + asset));
        return new Bitmap(stream);
    }

    private static Avalonia.Controls.Shapes.Path SvgIcon(string name, double size, IBrush? color = null)
    {
        var path = new Avalonia.Controls.Shapes.Path
        {
            Width = size,
            Height = size,
            Stretch = Stretch.Uniform,
            Data = StreamGeometry.Parse(IconPath(name)),
            Stroke = color ?? AccentBrush,
            StrokeThickness = 1.9,
            StrokeLineCap = PenLineCap.Round,
            StrokeJoin = PenLineJoin.Round,
            Fill = Brushes.Transparent,
            HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Center
        };
        return path;
    }

    private static string IconPath(string name) => name switch
    {
        "home" => "M4 11.5 12 5l8 6.5V20h-5v-5H9v5H4v-8.5Z",
        "list" => "M5 6h14M5 12h14M5 18h14M3 6h.01M3 12h.01M3 18h.01",
        "search" => "M10.8 18.6a7.8 7.8 0 1 1 0-15.6 7.8 7.8 0 0 1 0 15.6Zm5.6-2.2L21 21",
        "plus" => "M12 5v14M5 12h14",
        "download" => "M12 3v11m0 0 4-4m-4 4-4-4M5 19h14",
        "upload" => "M12 21V10m0 0 4 4m-4-4-4 4M5 5h14",
        "shield" => "M12 3l7 3v5c0 5-3.4 8.5-7 10-3.6-1.5-7-5-7-10V6l7-3Zm-3 9 2 2 4-4",
        "vault" => "M6 10h12v10H6V10Zm3 0V8a3 3 0 0 1 6 0v2M12 14v3",
        "star" => "M12 3l2.8 5.7 6.2.9-4.5 4.4 1.1 6.1L12 17.7 6.4 20.6 7.5 14.5 3 10.1l6.2-.9L12 3Z",
        "spark" => "M12 3l1.7 5.4L19 10l-5.3 1.6L12 17l-1.7-5.4L5 10l5.3-1.6L12 3Zm6 12 .8 2.2L21 18l-2.2.8L18 21l-.8-2.2L15 18l2.2-.8L18 15Z",
        "settings" => "M12 8a4 4 0 1 1 0 8 4 4 0 0 1 0-8Zm8 4a7.8 7.8 0 0 0-.1-1.2l2-1.5-2-3.4-2.4 1a8 8 0 0 0-2-1.2L15.2 3H8.8l-.4 2.7a8 8 0 0 0-2 1.2l-2.4-1-2 3.4 2 1.5A7.8 7.8 0 0 0 4 12c0 .4 0 .8.1 1.2l-2 1.5 2 3.4 2.4-1a8 8 0 0 0 2 1.2l.4 2.7h6.4l.4-2.7a8 8 0 0 0 2-1.2l2.4 1 2-3.4-2-1.5c.1-.4.1-.8.1-1.2Z",
        "lock" => "M7 10V8a5 5 0 0 1 10 0v2M6 10h12v10H6V10Zm6 4v3",
        "key" => "M14.5 9.5a4 4 0 1 1-2.7-3.8A4 4 0 0 1 14.5 9.5Zm0 0H21v3h-2v2h-3v-2h-1.5",
        "trash" => "M6 7h12M10 11v6M14 11v6M9 7l1-3h4l1 3M8 7l1 13h6l1-13",
        "privacy" => "M12 3c3 2 5.5 2.5 8 3v6c0 4.8-3.6 7.7-8 9-4.4-1.3-8-4.2-8-9V6c2.5-.5 5-1 8-3Z",
        "document" => "M7 3h7l4 4v14H7V3Zm7 0v5h5M10 13h5M10 17h5",
        "info" => "M12 17v-6M12 8h.01M12 22a10 10 0 1 1 0-20 10 10 0 0 1 0 20Z",
        "copy" => "M8 8h11v11H8V8Zm-3 8H4V4h12v1",
        "edit" => "M4 20h4l11-11-4-4L4 16v4Zm10-14 4 4",
        "check" => "M20 6 9 17l-5-5",
        "close" => "M6 6l12 12M18 6 6 18",
        "arrow" => "M5 12h14m-5-5 5 5-5 5",
        "arrow-left" => "M19 12H5m5-5-5 5 5 5",
        "eye" => "M3 12s3.5-6 9-6 9 6 9 6-3.5 6-9 6-9-6-9-6Zm9 2.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5Z",
        "minus" => "M5 12h14",
        _ => "M7 10V8a5 5 0 0 1 10 0v2M6 10h12v10H6V10Zm6 4v3"
    };
}

internal static class UiExtensions
{
    public static T Also<T>(this T value, Action<T> action)
    {
        action(value);
        return value;
    }
}


