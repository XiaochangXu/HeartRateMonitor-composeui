using System.Collections.ObjectModel;
using HeartRate.Helpers;
using HeartRate.Models;
using HeartRate.Services;
using Microsoft.UI.Dispatching;

namespace HeartRate.ViewModels
{
    /// <summary>左侧设备列表：BLE 广播扫描、设备集合与选择。</summary>
    public partial class DeviceListViewModel : ObservableObject
    {
        private readonly HeartRateService _service;
        private readonly DispatcherQueue _uiDispatcher;

        public ObservableCollection<BleDeviceInfo> Devices { get; } = new();

        [ObservableProperty]
        private bool _isScanning;

        [ObservableProperty]
        [NotifyPropertyChangedFor(nameof(HasDevices))]
        private BleDeviceInfo? _selectedDevice;

        public DeviceListViewModel(HeartRateService service, DispatcherQueue uiDispatcher)
        {
            _service = service;
            _uiDispatcher = uiDispatcher;
            _service.DeviceDiscovered += OnDeviceDiscovered;
        }

        public string ScanButtonContent => IsScanning ? L.DeviceList_StopScan : L.DeviceList_StartScan;

        public Visibility EmptyHintVisibility => Devices.Count == 0 ? Visibility.Visible : Visibility.Collapsed;

        public bool HasDevices => Devices.Count > 0;

        partial void OnIsScanningChanged(bool value)
        {
            OnPropertyChanged(nameof(ScanButtonContent));
        }

        private void OnDeviceDiscovered(object? sender, BleDeviceInfo device)
        {
            _uiDispatcher.TryEnqueue(() =>
            {
                Devices.Add(device);
                OnPropertyChanged(nameof(EmptyHintVisibility));
                OnPropertyChanged(nameof(HasDevices));
            });
        }

        [RelayCommand]
        private void ToggleScan()
        {
            if (IsScanning)
            {
                _service.StopScan();
                IsScanning = false;
            }
            else
            {
                Devices.Clear();
                OnPropertyChanged(nameof(EmptyHintVisibility));
                OnPropertyChanged(nameof(HasDevices));
                _service.StartScan();
                IsScanning = true;
            }
        }

        [RelayCommand]
        private void Clear()
        {
            Devices.Clear();
            SelectedDevice = null;
            OnPropertyChanged(nameof(EmptyHintVisibility));
            OnPropertyChanged(nameof(HasDevices));
        }
    }
}
