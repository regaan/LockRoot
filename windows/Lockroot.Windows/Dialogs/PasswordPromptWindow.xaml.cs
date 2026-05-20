using System.Windows;

namespace Lockroot.Windows.Dialogs;

public partial class PasswordPromptWindow : Window
{
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

    public string Password { get; private set; } = "";

    private void ContinueClick(object sender, RoutedEventArgs e)
    {
        ErrorText.Text = "";

        if (PasswordBox.Password.Length < 8)
        {
            ErrorText.Text = "Use at least 8 characters.";
            return;
        }

        if (_requiresConfirmation && PasswordBox.Password != ConfirmPasswordBox.Password)
        {
            ErrorText.Text = "Passwords do not match.";
            return;
        }

        Password = PasswordBox.Password;
        DialogResult = true;
    }

    private void CancelClick(object sender, RoutedEventArgs e)
    {
        DialogResult = false;
    }
}
