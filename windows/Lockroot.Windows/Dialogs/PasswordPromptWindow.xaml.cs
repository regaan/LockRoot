using System.Windows;
using Lockroot.Windows.Security;
using Lockroot.Windows.Services;

namespace Lockroot.Windows.Dialogs;

public partial class PasswordPromptWindow : Window
{
    private const int MinimumPasswordLength = 12;
    private readonly bool _requiresConfirmation;

    public PasswordPromptWindow(string title, string message, bool requiresConfirmation)
    {
        InitializeComponent();
        _requiresConfirmation = requiresConfirmation;
        TitleText.Text = title;
        MessageText.Text = message;
        PasswordLabel.Text = requiresConfirmation ? "Export password" : "Import password";
        ConfirmPasswordLabel.Visibility = requiresConfirmation ? Visibility.Visible : Visibility.Collapsed;
        ConfirmPasswordBox.Visibility = requiresConfirmation ? Visibility.Visible : Visibility.Collapsed;
        PasswordBox.Focus();
    }

    public char[] Password { get; private set; } = [];

    private void ContinueClick(object sender, RoutedEventArgs e)
    {
        ErrorText.Text = "";

        if (PasswordBox.Password.Length < MinimumPasswordLength)
        {
            ErrorText.Text = $"Use at least {MinimumPasswordLength} characters.";
            return;
        }

        if (_requiresConfirmation && PasswordBox.Password != ConfirmPasswordBox.Password)
        {
            ErrorText.Text = "Passwords do not match.";
            return;
        }

        Password = PasswordMemory.FromString(PasswordBox.Password);
        PasswordBox.Password = "";
        ConfirmPasswordBox.Password = "";
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

    protected override void OnClosed(EventArgs e)
    {
        PasswordBox.Password = "";
        ConfirmPasswordBox.Password = "";

        if (DialogResult != true)
        {
            WipePassword();
        }

        base.OnClosed(e);
    }

    public void WipePassword()
    {
        PasswordMemory.Wipe(Password);
        Password = [];
    }
}
