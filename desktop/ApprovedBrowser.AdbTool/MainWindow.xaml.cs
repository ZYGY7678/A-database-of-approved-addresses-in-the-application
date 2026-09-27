using System;
using System.Diagnostics;
using System.Text;
using System.Threading.Tasks;
using System.Windows;
namespace ApprovedBrowser.AdbTool;
public partial class MainWindow : Window
{
    private const string PackageName="com.zygy7678.approvedbrowser";
    private const string AdminComponent=PackageName+"/.ApprovedBrowserDeviceAdminReceiver";
    public MainWindow(){InitializeComponent();Loaded+=async(_,_)=>await RefreshStatusAsync();}
    private async void RefreshButton_Click(object sender,RoutedEventArgs e)=>await RefreshStatusAsync();
    private async Task RefreshStatusAsync(){SetBusy(true);try{var r=await RunAdbAsync("get-state");if(r.ExitCode==0&&r.Output.Trim().Equals("device",StringComparison.OrdinalIgnoreCase)){ConnectionBadge.Text="מחובר";ConnectionBadge.Foreground=System.Windows.Media.Brushes.LightGreen;DeviceStatusText.Text="מכשיר Android מחובר ומוכן לקבלת פקודת ADB";AppendLog("✓ ADB: המכשיר מחובר");}else{ConnectionBadge.Text="לא מחובר";ConnectionBadge.Foreground=System.Windows.Media.Brushes.Orange;DeviceStatusText.Text="חבר את המכשיר ב־USB ואשר ניפוי USB";AppendLog("! ADB: המכשיר לא זוהה");if(!string.IsNullOrWhiteSpace(r.Error))AppendLog(r.Error.Trim());}}catch(Exception ex){ConnectionBadge.Text="ADB חסר";ConnectionBadge.Foreground=System.Windows.Media.Brushes.OrangeRed;DeviceStatusText.Text="לא נמצא adb במחשב. ודא שהוא נמצא ב־PATH";AppendLog("✕ "+ex.Message);}finally{SetBusy(false);}}
    private async void SetOwnerButton_Click(object sender,RoutedEventArgs e){var a=MessageBox.Show("הפקודה תגדיר את דפדפן מאושר כבעל המכשיר.\n\nהמשך?","אישור הגנת המכשיר",MessageBoxButton.YesNo,MessageBoxImage.Question);if(a!=MessageBoxResult.Yes)return;SetBusy(true);try{AppendLog("> שולח פקודת ADB...");var r=await RunAdbAsync($"shell dpm set-device-owner {AdminComponent}");if(r.ExitCode==0){AppendLog("✓ פקודת ADB בוצעה בהצלחה");if(!string.IsNullOrWhiteSpace(r.Output))AppendLog(r.Output.Trim());MessageBox.Show("הגנת המכשיר הוגדרה בהצלחה","בוצע",MessageBoxButton.OK,MessageBoxImage.Information);}else{AppendLog("✕ הפקודה נכשלה");if(!string.IsNullOrWhiteSpace(r.Error))AppendLog(r.Error.Trim());MessageBox.Show(string.IsNullOrWhiteSpace(r.Error)?"הפקודה נכשלה":r.Error.Trim(),"שגיאת ADB",MessageBoxButton.OK,MessageBoxImage.Error);}await RefreshStatusAsync();}catch(Exception ex){AppendLog("✕ "+ex.Message);MessageBox.Show(ex.Message,"שגיאה",MessageBoxButton.OK,MessageBoxImage.Error);}finally{SetBusy(false);}}
    private static async Task<AdbResult> RunAdbAsync(string arguments){var psi=new ProcessStartInfo{FileName="adb",Arguments=arguments,UseShellExecute=false,RedirectStandardOutput=true,RedirectStandardError=true,CreateNoWindow=true,StandardOutputEncoding=Encoding.UTF8,StandardErrorEncoding=Encoding.UTF8};using var p=new Process{StartInfo=psi};p.Start();var o=p.StandardOutput.ReadToEndAsync();var e=p.StandardError.ReadToEndAsync();await p.WaitForExitAsync();return new AdbResult(p.ExitCode,await o,await e);}
    private void SetBusy(bool busy){SetOwnerButton.IsEnabled=!busy;System.Windows.Input.Mouse.OverrideCursor=busy?System.Windows.Input.Cursors.Wait:null;}
    private void AppendLog(string t){LogBox.AppendText($"[{DateTime.Now:HH:mm:ss}] {t}{Environment.NewLine}");LogBox.ScrollToEnd();}
    private sealed record AdbResult(int ExitCode,string Output,string Error);
}