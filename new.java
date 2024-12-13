package project702;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Main simulation class that ties all components together
public class Simulator {
    private InstructionQueue instructionQueue;
    private CDB cdb;
    private List<FunctionalUnit> functionalUnits;
    private List<ReservationStation> reservationStations;
    private int currentCycle;
    private RegisterFile registerFile;
    private static final int MAX_CYCLES = 100;

    public Simulator() {
        this.instructionQueue = new InstructionQueue();
        this.cdb = new CDB(4); // Example: 4 reservation stations
        this.reservationStations = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            reservationStations.add(new ReservationStation());
        }
        this.functionalUnits = new ArrayList<>();
        initializeFunctionalUnits();
        this.currentCycle = 0;
        this.registerFile = new RegisterFile();
    }

    private void initializeFunctionalUnits() {
        functionalUnits.add(new FunctionalUnit("ADD", 2));       // Adder with 2-cycle latency
        functionalUnits.add(new FunctionalUnit("MUL", 10));      // Multiplier with 10-cycle latency
        functionalUnits.add(new FunctionalUnit("LOAD", 2));      // Load with 2-cycle latency
        functionalUnits.add(new FunctionalUnit("BRANCH", 1));    // Branch with 1-cycle latency
    }

    public void runSimulation() {
        while (true) {
            System.out.println("Current cycle: " + currentCycle);
            
            if (currentCycle >= MAX_CYCLES) {
                System.out.println("Maximum cycle limit reached, stopping simulation.");
                break;
            }
            // Write-Back Stage: Process results on the CDB
            cdb.processCycle();

            // Execute Stage: Simulate Functional Units
            simulateFunctionalUnits();

            // Issue Stage: Fetch and dispatch instructions
            issueInstructions();

            currentCycle++;

            // Termination condition: All queues and units are idle
            if (instructionQueue.isEmpty() && allFunctionalUnitsIdle() && allReservationStationsIdle()) {
                System.out.println("Simulation complete at cycle: " + currentCycle);
                break;
            }
        }
    }

    private void issueInstructions() {
        while (!instructionQueue.isEmpty()) {
            Instructions instruction = instructionQueue.fetchNext();
            
            if (instruction.getOperation().equals("BNE")) {
                // Handle Branch if Not Equal (BNE)
                String src1 = instruction.getSrc1();
                String src2 = instruction.getSrc2();

                // Check if src1 and src2 are registers (start with 'R') or labels
                int r1Value = 0, r2Value = 0;
                if (src1.startsWith("R")) {
                    // It's a register, so parse the register number
                    r1Value = registerFile.readIntRegister(Integer.parseInt(src1.substring(1)));
                } else {
                    // Handle labels like "LOOP"
                    System.out.println("src1 is a label: " + src1);
                }

                if (src2.startsWith("R")) {
                    // It's a register, so parse the register number
                    r2Value = registerFile.readIntRegister(Integer.parseInt(src2.substring(1)));
                } else {
                    // Handle labels like "LOOP"
                    System.out.println("src2 is a label: " + src2);
                }

                // If R1 != R2, jump to loop (address of L.D)
                if (r1Value != r2Value) {
                    System.out.println("Branching to LOOP...");
                    instructionQueue.addInstruction(instruction);  // Reissue instruction for the next loop cycle
                    break;
                }
            }

            // Find an available reservation station
            ReservationStation availableRS = findAvailableReservationStation();
            if (availableRS != null) {
            	System.out.println("Found available RS for instruction: " + instruction.getOperation());
                FunctionalUnit assignedUnit = getFunctionalUnitForInstruction(instruction.getOperation());
                if (assignedUnit != null) {
                    // Check operand readiness using the CDB
                    String vj = cdb.resolveDependency(instruction.getSrc1());
                    String vk = cdb.resolveDependency(instruction.getSrc2());

                    // Configure the reservation station
                    availableRS.setBusy(true);
                    availableRS.setOperation(instruction.getOperation());
                    availableRS.setDestination(instruction.getDestination());
                    availableRS.setVj(vj);
                    availableRS.setVk(vk);
                    availableRS.setExecCycles(assignedUnit.getLatency());
                    availableRS.setIssued(currentCycle);

                    // Add the reservation station to the functional unit's queue
                    assignedUnit.addToQueue(availableRS);

                    System.out.println("Issued instruction: " + instruction + " to RS: " + availableRS);
                } else {
                    System.out.println("No functional unit available for operation: " + instruction.getOperation());
                    instructionQueue.requeue(instruction); // Requeue the instruction
                    break; // Stop issuing for this cycle
                }
            } else {
                System.out.println("No available reservation station for instruction: " + instruction);
                instructionQueue.requeue(instruction); // Requeue the instruction
                break; // Stop issuing for this cycle
            }
        }
    }

    private void simulateFunctionalUnits() {
        for (FunctionalUnit unit : functionalUnits) {
            unit.execute(currentCycle);
        }
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
    	    if (operation.startsWith("MUL") || operation.startsWith("DIV")) return "MUL";
    	    if (operation.startsWith("L.D") || operation.startsWith("S.D")) return "LOAD";
    	    if (operation.startsWith("BRANCH")) return null;  // No functional unit for branches
    	    return null; // Default case
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
