# Problem Statement

## 1. Title

**Loved Ones Gifting Platform**

---

## 2. Domain

**E-Commerce / Online Gifting and Delivery Management**

---

## 3. Who is the User?

The system is designed for the following user types:

### 3.1 Customer

The primary user of the platform who can browse gifts, place orders, provide recipient and delivery details, and track order status.

### 3.2 Administrator

The system administrator responsible for managing gifts, categories, inventory, customer orders, and delivery information.

### 3.3 Delivery Staff

The delivery personnel responsible for viewing assigned deliveries and updating the delivery status.

---

## 4. What Problem Are We Solving?

Selecting and sending appropriate gifts to loved ones can be challenging, especially when people are geographically separated or have limited time to visit physical stores. Existing e-commerce platforms provide a large variety of products, but the gifting process can involve searching across multiple categories and separately managing recipient and delivery information. For example, a customer who wants to send a birthday gift to a family member needs a convenient way to select a gift, provide delivery details, schedule the delivery, and track the order. The proposed system addresses this problem by providing a centralized platform specifically designed to simplify the gift selection, ordering, and delivery process.

---

## 5. Proposed Solution

The **Loved Ones Gifting Platform** is a web-based application designed to provide a simple and organized gifting experience.

The application will provide the following features:

### Customer Features

* User registration and login
* User profile management
* Browse available gifts
* Search and filter gifts by category
* View gift details and pricing
* Place gift orders
* Provide recipient and delivery details
* Schedule delivery dates
* View order history
* Track order and delivery status

### Administrator Features

* Manage customer accounts
* Add, update, and remove gift products
* Manage gift categories
* Manage product inventory
* View and manage customer orders
* Update order status
* Manage delivery information
* View basic system reports

### Delivery Features

* View assigned deliveries
* View recipient and delivery details
* Update delivery status
* Confirm successful delivery

The initial MVP will focus on implementing the core user, gift, order, database, and delivery management functionalities.

---

## 6. Core Entities / Database Tables

The system will use the following core database entities:

| No. | Entity / Table | Purpose                                           |
| --: | -------------- | ------------------------------------------------- |
|   1 | `Users`        | Stores customer and user account information      |
|   2 | `Categories`   | Stores gift categories                            |
|   3 | `Gifts`        | Stores gift/product details, price, and inventory |
|   4 | `Orders`       | Stores customer order information                 |
|   5 | `Order_Items`  | Stores individual gifts included in each order    |
|   6 | `Payments`     | Stores payment details and payment status         |
|   7 | `Deliveries`   | Stores delivery and tracking information          |

### Entity Relationships

* A **User** can place multiple **Orders**.
* A **Category** can contain multiple **Gifts**.
* An **Order** can contain multiple **Order Items**.
* A **Gift** can be included in multiple **Order Items**.
* An **Order** is associated with a **Payment**.
* An **Order** is associated with a **Delivery**.

---

## 7. User Roles & Permissions

The system provides role-based access to ensure that users can perform only the operations relevant to their responsibilities.

| Functionality          | Customer | Administrator | Delivery Staff |
| ---------------------- | :------: | :-----------: | :------------: |
| Register / Login       |     ✓    |       ✓       |        ✓       |
| Browse Gifts           |     ✓    |       ✓       |        —       |
| Search Gifts           |     ✓    |       ✓       |        —       |
| Place Order            |     ✓    |       —       |        —       |
| View Order History     |     ✓    |       ✓       |        —       |
| Manage Gifts           |     —    |       ✓       |        —       |
| Manage Categories      |     —    |       ✓       |        —       |
| Manage Inventory       |     —    |       ✓       |        —       |
| Manage Orders          |     —    |       ✓       |        —       |
| View Delivery Details  |     —    |       ✓       |        ✓       |
| Update Delivery Status |     —    |       ✓       |        ✓       |
| View Reports           |     —    |       ✓       |        —       |

---

## 8. Success Criteria

The MVP will be considered successful when the following core requirements are achieved:

1. A customer should be able to register and log in successfully.
2. A customer should be able to browse and search available gifts.
3. A customer should be able to view gift details, pricing, and availability.
4. A customer should be able to place an order successfully.
5. Order and customer information should be stored correctly in the database.
6. An administrator should be able to manage gifts, categories, and inventory.
7. An administrator should be able to view and manage customer orders.
8. Authorized users should be able to update delivery status.
9. A customer should be able to view the current status of an order.
10. The complete basic gifting workflow should be demonstrable through the MVP without manual modification of database records.

---

## 9. Out of Scope

The following features are intentionally excluded from the initial MVP to maintain a realistic and achievable project scope:

* Real-time GPS delivery tracking
* Integration with external courier services
* Production-level payment gateway integration
* Native Android or iOS mobile application
* Voice-based ordering
* Social media integration
* International delivery management
* Multi-vendor marketplace functionality
* Advanced business intelligence and analytics
* Production-scale cloud infrastructure
* Advanced AI/ML recommendation models

These features may be considered as future enhancements after the successful completion of the initial MVP.

---

## 10. Chosen Track

**Java (Spring Boot)**

### Technology Stack

* **Programming Language:** Java
* **Backend Framework:** Spring Boot
* **API Architecture:** REST API
* **Database:** MySQL
* **Database Access:** Spring Data JPA / Hibernate
* **Development Environment:** Visual Studio Code
* **Version Control:** Git and GitHub

### Initial MVP Scope

The first version of the application will focus on:

1. User Management
2. Gift and Category Management
3. Inventory Management
4. Order Management
5. Payment Status Management
6. Delivery Management
7. MySQL Database Integration

---

## Project Objective

The objective of the **Loved Ones Gifting Platform** is to provide a centralized and convenient solution for selecting, ordering, scheduling, and tracking gifts for loved ones.

The MVP will establish the core application and database architecture required for future enhancements such as personalized AI-based gift recommendations, online payment integration, real-time delivery tracking, and cloud deployment.
