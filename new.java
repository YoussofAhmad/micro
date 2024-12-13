package project702;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import project702.Cache.CacheEntry;

// Main simulation class that ties all components together
public class Simulator {
    private List<SimulationState> simulationHistory;
    private List<ReservationStation> reservationStations;
    private int currentCycle;
    private RegisterFile registerFile;
    private Cache cache;
    private List<ReservationStation> addReservationStations;
    private List<ReservationStation> mulReservationStations;
    private List<LoadStoreBuffer> loadStoreBuffers;
    private InstructionQueue instructionQueue;
    private CDB cdb;
    private Manager manager;
    private List<FunctionalUnit> functionalUnits;

    public class SimulationState {
        List<ReservationStation> addRS;
        List<ReservationStation> mulRS;
        List<LoadStoreBuffer> loadBuffers;
        RegisterFile regFile;
        Cache cacheState;
        InstructionQueue instructionQueueState;
        int cycle;

        public SimulationState(List<ReservationStation> addRS, List<ReservationStation> mulRS,
                             List<LoadStoreBuffer> loadBuffers, RegisterFile regFile, 
                             Cache cacheState, InstructionQueue instructionQueue, int cycle) {
            this.addRS = deepCopyList(addRS);
            this.mulRS = deepCopyList(mulRS);
            this.loadBuffers = deepCopyList(loadBuffers);
            this.regFile = regFile.deepCopy();
            this.cacheState = cacheState.deepCopy();
            this.instructionQueueState = new InstructionQueue();
            for (Instructions inst : instructionQueue.getInstructions()) {
                this.instructionQueueState.addInstruction(inst);
            }
            this.cycle = cycle;
        }
    }

    public Simulator(int cacheSize, int blockSize) {
        this.simulationHistory = new ArrayList<>();
        this.currentCycle = 0;
        this.registerFile = new RegisterFile();
        this.cache = new Cache(cacheSize, 2, cacheSize * blockSize);
        this.addReservationStations = new ArrayList<>();
        this.mulReservationStations = new ArrayList<>();
        this.loadStoreBuffers = new ArrayList<>();
        this.instructionQueue = new InstructionQueue();
        this.cdb = new CDB(5);
        this.manager = new Manager();
        this.functionalUnits = new ArrayList<>();
        this.reservationStations = new ArrayList<>(); // Initialize the missing field

        // Initialize reservation stations
        for (int i = 0; i < 3; i++) {
            ReservationStation rs = new ReservationStation("ADD" + i);
            addReservationStations.add(rs);
            reservationStations.add(rs);
        }
        for (int i = 0; i < 2; i++) {
            ReservationStation rs = new ReservationStation("MUL" + i);
            mulReservationStations.add(rs);
            reservationStations.add(rs);
        }
        for (int i = 0; i < 3; i++) {
            loadStoreBuffers.add(new LoadStoreBuffer());
        }

        initializeFunctionalUnits();
        saveCurrentState();
    }
    


    private void executeCurrentCycle() {
        // Update reservation stations
        for (ReservationStation rs : addReservationStations) {
            if (rs.isBusy() && !rs.isCompleted()) {
                rs.execute(currentCycle);
            }
        }
        for (ReservationStation rs : mulReservationStations) {
            if (rs.isBusy() && !rs.isCompleted()) {
                rs.execute(currentCycle);
            }
        }

        // Update load/store buffers
        for (LoadStoreBuffer lsb : loadStoreBuffers) {
            if (lsb.isBusy()) {
                // Simulate memory access
                if (lsb.getAddress() != -1) {
                    byte[] data = cache.accessMemory(lsb.getAddress());
                    // Process the loaded data...
                }
            }
        }
    }

    // Helper method for deep copying lists
  

    private void initializeFunctionalUnits() {
        functionalUnits.add(new FunctionalUnit("ADD", 2));
        functionalUnits.add(new FunctionalUnit("MUL", 10));
        functionalUnits.add(new FunctionalUnit("DIV", 40));
        functionalUnits.add(new FunctionalUnit("LOAD", 2));
    }

    public void runSimulationStep() {
        currentCycle++;
        System.out.println("\nExecuting cycle " + currentCycle);

        // 1. Issue Stage
        issueInstructions();

        // 2. Execute Stage
        executeInstructions();

        // 3. Write-Back Stage
        cdb.processCycle();

        // Save state after all stages
        saveCurrentState();
    }

    private void issueInstructions() {
        if (!instructionQueue.isEmpty()) {
            Instructions instruction = instructionQueue.fetchNext();
            if (instruction != null) {
                ReservationStation availableRS = findAvailableReservationStation(instruction.getOperation());
                if (availableRS != null) {
                    // Configure reservation station
                    configureReservationStation(availableRS, instruction);
                    System.out.println("Issued: " + instruction.getOperation() + " to " + availableRS.getName());
                } else {
                    instructionQueue.requeue(instruction);
                    System.out.println("No available reservation station for: " + instruction.getOperation());
                }
            }
        }
    }
    private void executeInstructions() {
        // Execute instructions in Add/Sub reservation stations
        for (ReservationStation rs : addReservationStations) {
            if (rs.isBusy() && !rs.isCompleted() && rs.isReady()) {
                rs.execute(currentCycle);
                if (rs.isCompleted()) {
                    handleInstructionCompletion(rs);
                }
            }
        }

        // Execute instructions in Mul/Div reservation stations
        for (ReservationStation rs : mulReservationStations) {
            if (rs.isBusy() && !rs.isCompleted() && rs.isReady()) {
                rs.execute(currentCycle);
                if (rs.isCompleted()) {
                    handleInstructionCompletion(rs);
                }
            }
        }

        // Handle load/store operations
        for (LoadStoreBuffer lsb : loadStoreBuffers) {
            if (lsb.isBusy()) {
                processLoadStore(lsb);
            }
        }
    }
    private void processLoadStore(LoadStoreBuffer lsb) {
        if (lsb.getAddress() != -1) {
            byte[] data = cache.accessMemory(lsb.getAddress());
            // Process loaded data
            lsb.completeLoad(ByteBuffer.wrap(data).getInt());
        }
    }
    private float calculateResult(ReservationStation rs) {
        float vj = Float.parseFloat(rs.getVj());
        float vk = Float.parseFloat(rs.getVk());
        
        switch (rs.getOperation().toUpperCase()) {
            case "ADD.D":
                return vj + vk;
            case "SUB.D":
                return vj - vk;
            case "MUL.D":
                return vj * vk;
            case "DIV.D":
                return vj / vk;
            default:
                return 0;
        }
    }
    private void configureReservationStation(ReservationStation rs, Instructions instruction) {
        rs.setBusy(true);
        rs.setOperation(instruction.getOperation());
        rs.setDestination(instruction.getDestination());
        
        // Get values or dependencies from CDB
        String vj = cdb.resolveDependency(instruction.getSrc1());
        String vk = cdb.resolveDependency(instruction.getSrc2());
        
        rs.setVj(vj);
        rs.setVk(vk);
        
        // Set dependencies if values not available
        if (vj == null) rs.setQj(instruction.getSrc1());
        if (vk == null) rs.setQk(instruction.getSrc2());
        
        rs.setIssued(currentCycle);
        rs.setExecCycles(getLatencyForOperation(instruction.getOperation()));
    }

    private int getLatencyForOperation(String operation) {
        if (operation.startsWith("ADD") || operation.startsWith("SUB")) return 2;
        if (operation.startsWith("MUL")) return 10;
        if (operation.startsWith("DIV")) return 40;
        if (operation.startsWith("L.") || operation.startsWith("S.")) return 2;
        return 1;
    }
    private void handleInstructionCompletion(ReservationStation rs) {
        // Calculate result
        float result = calculateResult(rs);
        // Broadcast result on CDB
        cdb.broadcast(rs.getDestination(), result);
        // Clear reservation station
        rs.clear();
    }
    public boolean isSimulationComplete() {
        return instructionQueue.isEmpty() && 
               allReservationStationsIdle() && 
               allFunctionalUnitsIdle();
    }
    
    // Add method to get cache entries
    public Map<Integer, CacheEntry> getCacheEntries() {
        return cache.getEntries();
    }

    public List<ReservationStation> getAddReservationStations() {
        return new ArrayList<>(addReservationStations);
    }

    public List<ReservationStation> getMulReservationStations() {
        return new ArrayList<>(mulReservationStations);
    }
    public List<LoadStoreBuffer> getLoadStoreBuffers() {
        return new ArrayList<>(loadStoreBuffers);
    }

    public RegisterFile getRegisterFile() {
        return registerFile;
    }

    public Cache getCache() {
        return cache;
    }

    public int getCurrentCycle() {
        return currentCycle;
    }

    private ReservationStation findAvailableReservationStation(String operation) {
        List<ReservationStation> targetList = operation.startsWith("MUL") || operation.startsWith("DIV") 
            ? mulReservationStations 
            : addReservationStations;
            
        return targetList.stream()
            .filter(rs -> !rs.isBusy())
            .findFirst()
            .orElse(null);
    }
    
   
    private <T> List<T> deepCopyList(List<T> original) {
        List<T> copy = new ArrayList<>();
        for (T item : original) {
            if (item instanceof ReservationStation) {
                copy.add((T) ((ReservationStation) item).deepCopy());
            } else if (item instanceof LoadStoreBuffer) {
                copy.add((T) ((LoadStoreBuffer) item).deepCopy());
            }
        }
        return copy;
    }
    public void revertToPreviousCycle() {
        if (currentCycle > 0 && !simulationHistory.isEmpty()) {
            currentCycle--;
            SimulationState previousState = simulationHistory.get(currentCycle);
            
            // Restore all components to previous state
            addReservationStations = previousState.addRS;
            mulReservationStations = previousState.mulRS;
            loadStoreBuffers = previousState.loadBuffers;
            registerFile = previousState.regFile;
            cache = previousState.cacheState;
            instructionQueue = previousState.instructionQueueState;
            
            System.out.println("Reverted to cycle " + currentCycle);
        }
    }
    private void saveCurrentState() {
        SimulationState state = new SimulationState(
            addReservationStations, 
            mulReservationStations,
            loadStoreBuffers,
            registerFile,
            cache,
            instructionQueue, // Add missing parameter
            currentCycle
        );
        simulationHistory.add(state);
    }

    private ReservationStation findAvailableReservationStation() {
        return reservationStations.stream().filter(rs -> !rs.isBusy()).findFirst().orElse(null);
    }

    private FunctionalUnit getFunctionalUnitForInstruction(String operation) {
        return functionalUnits.stream()
                .filter(unit -> unit.getType().equalsIgnoreCase(mapOperationToFunctionalUnit(operation)))
                .findFirst()
                .orElse(null);
    }

    private String mapOperationToFunctionalUnit(String operation) {
        // Map instruction types to functional unit types
        if (operation.startsWith("ADD") || operation.startsWith("SUB")) return "ADD";
        if (operation.startsWith("MUL")) return "MUL";
        if (operation.startsWith("DIV")) return "MUL"; // Assuming multiplier handles division too
        if (operation.startsWith("L.D") || operation.startsWith("S.D")) return "LOAD";
        if (operation.startsWith("BRANCH")) return "BRANCH";
        return null; // Unknown operation
    }

    private boolean allFunctionalUnitsIdle() {
        return functionalUnits.stream().allMatch(unit -> unit.isIdle());
    }

    private boolean allReservationStationsIdle() {
        return reservationStations.stream().allMatch(rs -> !rs.isBusy());
    }

    public void addInstructionToQueue(Instructions instruction) {
        instructionQueue.addInstruction(instruction);
    }
}