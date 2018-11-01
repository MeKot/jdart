package gov.nasa.jpf.jdart.coordinator;

import java.io.SyncFailedException;

public interface CoordinatorAdapter<T> {

    void passValue(T value) throws SyncFailedException;

}
