package n_body_simulation;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;


/**
 * NBodySimulation - A simulation of gravitational interactions among particles.
 * This Java Swing-based application simulates an N-body gravitational system,
 * including interactions, collisions, and merging of particles.
 */
public class NBodySimulation extends JPanel implements MouseListener, MouseMotionListener {

    private static final Logger LOGGER = Logger.getLogger(NBodySimulation.class.getName());

    // --- Simulation and GUI Constants ---
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final double DT = 0.1;
    private static final double G = 1.0;
    private static final double EPSILON = 5.0;
    private static final int INITIAL_PARTICLE_COUNT = 100;
    private static final Random RAND = new Random();


    // --- List of particles ---
    private final List<Particle> particles = new ArrayList<>();

    // --- Mouse variables for interactive input ---
    private int prevMouseX = -1;
    private int prevMouseY = -1;


    /**
     * Constructor: Initializes the GUI, generates particles, and starts a timer
     * for updating the simulation at approximately 60 FPS.
     */
    public NBodySimulation() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.BLACK);
        addMouseListener(this);
        addMouseMotionListener(this);

        for (int i = 0; i < INITIAL_PARTICLE_COUNT; i++) {
            double x = RAND.nextDouble() * WIDTH;
            double y = RAND.nextDouble() * HEIGHT;
            double vx = (RAND.nextDouble() - 0.5) * 2;
            double vy = (RAND.nextDouble() - 0.5) * 2;
            double mass = RAND.nextDouble() * 10 + 5;
            particles.add(new Particle(x, y, vx, vy, mass));
        }

        // Start the timer (~60 FPS)
        int delay = 1000 / 60;
        Timer timer = new Timer(delay, _ -> {
            updateSimulation();
            repaint();
        });
        timer.start();
    }

    /**
     * Inner class representing a particle in the simulation.
     */
    private static class Particle {
        double x;
        double y;
        double vx;
        double vy;
        double ax;
        double ay;
        double mass;
        double radius;
        Color color;

        Particle(double x, double y, double vx, double vy, double mass) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.mass = mass;
            this.radius = Math.cbrt(mass) * 2;
            this.color = new Color(
                    RAND.nextInt(200) + 55,
                    RAND.nextInt(200) + 55,
                    RAND.nextInt(200) + 55
            );
        }
    }

    /**
     * Updates the simulation:
     * - Computes gravitational forces
     * - Updates velocities and positions
     * - Applies reflective boundary conditions
     * - Detects and merges colliding particles
     */
    private void updateSimulation() {
        synchronized (particles) {
            final int n = particles.size();

            // Set accelerations to 0
            for (Particle p : particles) {
                p.ax = 0;
                p.ay = 0;
            }

            // Compute gravitational forces in parallel (O(n²) computation)
            IntStream.range(0, n).parallel().forEach(i -> {
                Particle pi = particles.get(i);
                double ax = 0;
                double ay = 0;
                for (int j = 0; j < n; j++) {
                    if (i == j) continue;
                    Particle pj = particles.get(j);
                    double dx = pj.x - pi.x;
                    double dy = pj.y - pi.y;
                    double distSq = dx * dx + dy * dy + EPSILON;
                    double dist = Math.sqrt(distSq);
                    // Compute force: F = G * m1 * m2 / dist²
                    double force = G * pi.mass * pj.mass / distSq;
                    // Acceleration: a = F / m, direction normalized
                    ax += force * dx / (dist * pi.mass);
                    ay += force * dy / (dist * pi.mass);
                }
                pi.ax = ax;
                pi.ay = ay;
            });

            // Update velocities and positions
            for (Particle p : particles) {
                p.vx += p.ax * DT;
                p.vy += p.ay * DT;
                p.x += p.vx * DT;
                p.y += p.vy * DT;
                // Reflective boundary conditions:
                if (p.x < 0) {
                    p.x = 0;
                    p.vx = -p.vx;
                }
                if (p.x > WIDTH) {
                    p.x = WIDTH;
                    p.vx = -p.vx;
                }
                if (p.y < 0) {
                    p.y = 0;
                    p.vy = -p.vy;
                }
                if (p.y > HEIGHT) {
                    p.y = HEIGHT;
                    p.vy = -p.vy;
                }
            }

            // Collision detection and merging (O(n²)):
            // If two particles touch, they merge into a new particle.
            List<Particle> toRemove = new ArrayList<>();
            for (int i = 0; i < particles.size(); i++) {
                Particle p1 = particles.get(i);
                for (int j = i + 1; j < particles.size(); j++) {
                    Particle p2 = particles.get(j);
                    double dx = p2.x - p1.x;
                    double dy = p2.y - p1.y;
                    double dist = Math.sqrt(dx * dx + dy * dy);
                    if (dist < (p1.radius + p2.radius)) {
                        // Merging: Determine new mass and compute momentum conservation
                        double totalMass = p1.mass + p2.mass;
                        p1.vx = (p1.vx * p1.mass + p2.vx * p2.mass) / totalMass;
                        p1.vy = (p1.vy * p1.mass + p2.vy * p2.mass) / totalMass;
                        p1.x = (p1.x * p1.mass + p2.x * p2.mass) / totalMass;
                        p1.y = (p1.y * p1.mass + p2.y * p2.mass) / totalMass;
                        p1.mass = totalMass;
                        p1.radius = Math.cbrt(totalMass) * 2;
                        p1.color = blendColors(p1.color, p2.color);
                        toRemove.add(p2);
                    }
                }
            }
            particles.removeAll(toRemove);
        }
    }

    /**
     * Mixes two colors (simple averaging of RGB components).
     *
     * @param c1 First color
     * @param c2 Second color
     * @return Mixed color
     */
    private Color blendColors(Color c1, Color c2) {
        int r = (c1.getRed() + c2.getRed()) / 2;
        int g = (c1.getGreen() + c2.getGreen()) / 2;
        int b = (c1.getBlue() + c2.getBlue()) / 2;
        return new Color(r, g, b);
    }

    /**
     * Render method: Draws all particles as circles (with anti-aliasing).
     *
     * @param g Graphics object
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        synchronized (particles) {
            for (Particle p : particles) {
                g2d.setColor(p.color);
                int drawX = (int) (p.x - p.radius);
                int drawY = (int) (p.y - p.radius);
                int diameter = (int) (p.radius * 2);
                g2d.fillOval(drawX, drawY, diameter, diameter);
            }
        }
    }

    // --- Mouse Events ---
    // Left mouse button (drag): Adds a new particle, with its velocity
    // derived from the mouse movement.
    // Right mouse button: Resets the simulation.
    @Override
    public void mousePressed(final MouseEvent e) {
        if (SwingUtilities.isRightMouseButton(e)) {
            resetSimulation();
        } else {
            prevMouseX = e.getX();
            prevMouseY = e.getY();
        }
    }

    @Override
    public void mouseDragged(final MouseEvent e) {
        int x = e.getX();
        int y = e.getY();
        synchronized (particles) {
            // Determine the velocity based on the mouse movement
            double vx = (x - prevMouseX) * 0.5;
            double vy = (y - prevMouseY) * 0.5;
            double mass = 10 + Math.random() * 10;
            Particle p = new Particle(x, y, vx, vy, mass);
            particles.add(p);
        }
        prevMouseX = x;
        prevMouseY = y;
    }

    @Override
    public void mouseReleased(final MouseEvent e) {
        prevMouseX = -1;
        prevMouseY = -1;
    }

    @Override
    public void mouseMoved(final MouseEvent e) {
        // Intentionally left blank.
    }

    @Override
    public void mouseClicked(final MouseEvent e) {
        // Intentionally left blank.
    }

    @Override
    public void mouseEntered(final MouseEvent e) {
        // Intentionally left blank.
    }

    @Override
    public void mouseExited(final MouseEvent e) {
        // Intentionally left blank.
    }

    /**
     * Resets the simulation and generates INITIAL_PARTICLE_COUNT random particles.
     */
    private void resetSimulation() {
        synchronized (particles) {
            particles.clear();
            for (int i = 0; i < INITIAL_PARTICLE_COUNT; i++) {
                double x = RAND.nextDouble() * WIDTH;
                double y = RAND.nextDouble() * HEIGHT;
                double vx = (RAND.nextDouble() - 0.5) * 2;
                double vy = (RAND.nextDouble() - 0.5) * 2;
                double mass = RAND.nextDouble() * 10 + 5;
                particles.add(new Particle(x, y, vx, vy, mass));
            }
        }
    }

    /**
     * Main method: Starts the application in a JFrame.
     *
     * @param args Command-line arguments (not used)
     */
    public static void main(final String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Error setting Look and Feel", ex);
        }
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("N-Body Simulation");
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            NBodySimulation simulator = new NBodySimulation();
            frame.add(simulator);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}

