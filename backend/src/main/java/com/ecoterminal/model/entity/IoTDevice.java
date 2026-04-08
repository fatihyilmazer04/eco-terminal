package com.ecoterminal.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "iot_devices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IoTDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "device_id")
    private Long deviceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    @Column(name = "serial_number", nullable = false, unique = true, length = 100)
    private String serialNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false, length = 30)
    private DeviceType deviceType;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "firmware_ver", length = 50)
    private String firmwareVer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private DeviceStatus status = DeviceStatus.ONLINE;

    @Column(name = "installed_at")
    private Instant installedAt;
}
