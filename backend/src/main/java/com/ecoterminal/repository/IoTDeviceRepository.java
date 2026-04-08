package com.ecoterminal.repository;

import com.ecoterminal.model.entity.DeviceStatus;
import com.ecoterminal.model.entity.IoTDevice;
import com.ecoterminal.model.entity.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IoTDeviceRepository extends JpaRepository<IoTDevice, Long> {

    List<IoTDevice> findByZone(Zone zone);

    List<IoTDevice> findByStatus(DeviceStatus status);
}
