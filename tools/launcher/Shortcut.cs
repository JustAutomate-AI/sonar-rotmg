using System;
using System.Runtime.InteropServices;
using System.Runtime.InteropServices.ComTypes;
using System.Text;

/// Creates a shortcut that carries an application id. The running Sonar
/// window announces the same id, so Windows treats the shortcut and the
/// window as one program: a pinned shortcut lights up when Sonar runs, and
/// pinning the running window pins this shortcut rather than Java.
public static class SonarShortcut
{
    [ComImport, Guid("00021401-0000-0000-C000-000000000046")]
    private class ShellLink { }

    [ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid("000214F9-0000-0000-C000-000000000046")]
    private interface IShellLinkW
    {
        void GetPath([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder file, int max, IntPtr findData, uint flags);
        void GetIDList(out IntPtr idList);
        void SetIDList(IntPtr idList);
        void GetDescription([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder name, int max);
        void SetDescription([MarshalAs(UnmanagedType.LPWStr)] string name);
        void GetWorkingDirectory([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder dir, int max);
        void SetWorkingDirectory([MarshalAs(UnmanagedType.LPWStr)] string dir);
        void GetArguments([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder args, int max);
        void SetArguments([MarshalAs(UnmanagedType.LPWStr)] string args);
        void GetHotkey(out short hotkey);
        void SetHotkey(short hotkey);
        void GetShowCmd(out int showCmd);
        void SetShowCmd(int showCmd);
        void GetIconLocation([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder iconPath, int max, out int icon);
        void SetIconLocation([MarshalAs(UnmanagedType.LPWStr)] string iconPath, int icon);
        void SetRelativePath([MarshalAs(UnmanagedType.LPWStr)] string path, uint reserved);
        void Resolve(IntPtr window, uint flags);
        void SetPath([MarshalAs(UnmanagedType.LPWStr)] string file);
    }

    [ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid("886D8EEB-8CF2-4446-8D02-CDBA1DBDCF99")]
    private interface IPropertyStore
    {
        void GetCount(out uint count);
        void GetAt(uint index, out PropertyKey key);
        void GetValue(ref PropertyKey key, out PropVariant value);
        void SetValue(ref PropertyKey key, ref PropVariant value);
        void Commit();
    }

    [StructLayout(LayoutKind.Sequential, Pack = 4)]
    private struct PropertyKey
    {
        public Guid FormatId;
        public uint PropertyId;
    }

    // Only the string form is used; the padding gives the structure its full size on 64-bit Windows.
    [StructLayout(LayoutKind.Explicit, Size = 24)]
    private struct PropVariant
    {
        [FieldOffset(0)] public ushort Type;
        [FieldOffset(8)] public IntPtr Pointer;
    }

    private const ushort VT_LPWSTR = 31;

    private static PropertyKey AppIdKey()
    {
        PropertyKey key = new PropertyKey();
        key.FormatId = new Guid("9F4C2855-9F79-4B39-A8D0-E1D42DE1D5F3");
        key.PropertyId = 5;
        return key;
    }

    public static void Create(string shortcutPath, string target, string workingDirectory, string description, string appId)
    {
        IShellLinkW link = (IShellLinkW)new ShellLink();
        link.SetPath(target);
        link.SetWorkingDirectory(workingDirectory);
        link.SetDescription(description);
        link.SetIconLocation(target, 0);

        PropertyKey key = AppIdKey();
        PropVariant value = new PropVariant();
        value.Type = VT_LPWSTR;
        value.Pointer = Marshal.StringToCoTaskMemUni(appId);
        try
        {
            IPropertyStore store = (IPropertyStore)link;
            store.SetValue(ref key, ref value);
            store.Commit();
        }
        finally
        {
            Marshal.FreeCoTaskMem(value.Pointer);
        }
        ((IPersistFile)link).Save(shortcutPath, true);
    }

    /// The application id stored in a shortcut, or an empty string when it has none.
    public static string ReadAppId(string shortcutPath)
    {
        IShellLinkW link = (IShellLinkW)new ShellLink();
        ((IPersistFile)link).Load(shortcutPath, 0);
        PropertyKey key = AppIdKey();
        PropVariant value;
        ((IPropertyStore)link).GetValue(ref key, out value);
        if (value.Type != VT_LPWSTR || value.Pointer == IntPtr.Zero) return "";
        string id = Marshal.PtrToStringUni(value.Pointer);
        Marshal.FreeCoTaskMem(value.Pointer);
        return id;
    }
}
