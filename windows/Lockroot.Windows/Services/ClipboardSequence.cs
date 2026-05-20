using System.Runtime.InteropServices;

namespace Lockroot.Windows.Services;

public static class ClipboardSequence
{
    public static uint Current => GetClipboardSequenceNumber();

    [DllImport("user32.dll")]
    private static extern uint GetClipboardSequenceNumber();
}
