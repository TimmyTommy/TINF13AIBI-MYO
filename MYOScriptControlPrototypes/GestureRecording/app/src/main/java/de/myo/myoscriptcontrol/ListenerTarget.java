package de.myo.myoscriptcontrol;

import com.thalmic.myo.Pose;

/**
 * Created by felix on 21.03.2015.
 */
public interface ListenerTarget {
    void OnPose(Pose pose);
    void OnGridPositionUpdate(GridPosition position);
    void OnUpdateStatus(String status);
}
