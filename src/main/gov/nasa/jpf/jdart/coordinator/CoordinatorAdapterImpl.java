package gov.nasa.jpf.jdart.coordinator;

import seedbag.CoordinatorSeedBag;

import java.io.Serializable;
import java.io.SyncFailedException;

public class CoordinatorAdapterImpl<T extends Serializable> implements CoordinatorAdapter<T> {

    // TODO: Get host from the config file
    private static final CoordinatorSeedBag<Serializable> seedbag =
            new CoordinatorSeedBag<>("192.168.1.147", 8080);

    @Override
    public void passValue(T value) throws SyncFailedException {
        if (!seedbag.add(value)) {
            throw new SyncFailedException("Failed to send the value to seedbag");
        }
    }

}
